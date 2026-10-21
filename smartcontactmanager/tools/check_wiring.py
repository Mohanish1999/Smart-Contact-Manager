#!/usr/bin/env python3
"""Static wiring check (no Maven needed): templates <-> controllers <-> DTOs <-> JavaScript.
Run from the project root:  python3 tools/check_wiring.py
It cannot replace a rendered test, but it catches the classic mismatches: dead links, wrong form methods,
fields that do not exist on the form object, missing views/fragments, undefined JS handlers, missing DOM ids."""
import re, sys, pathlib

root = pathlib.Path(__file__).resolve().parent.parent
java = root / "src/main/java/com/smart"
res = root / "src/main/resources"
tpl = res / "templates"
problems, checks = [], 0
forms_seen = [0]

def ok(cond, msg):
    global checks
    checks += 1
    if not cond: problems.append(msg)

def read(p): return pathlib.Path(p).read_text(encoding="utf-8", errors="replace")

# ---------------------------------------------------------------- 1. controller mappings
mappings = []   # (method, regex)
views = set()
def path_regex(p): return re.compile("^" + re.sub(r"\{[^/}]+\}", r"[^/]+", p.rstrip("/") or "/") + "/?$")
for f in (java / "controller").glob("*.java"):
    src = read(f)
    base = ""
    m = re.search(r'@RequestMapping\("([^"]+)"\)\s*public class', src)
    if m: base = m.group(1)
    for am in re.finditer(r'@(Get|Post|Request)Mapping\(\s*(\{\s*"[^"]*"(?:\s*,\s*"[^"]*")*\s*\}|"[^"]*")', src):
        kind = am.group(1)
        after = src[am.end():am.end() + 200]
        if kind == "Request" and re.match(r'\s*\)\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+)?class\b', after): continue   # class-level prefix
        method = {"Get": "GET", "Post": "POST", "Request": "ANY"}[kind]
        for p in re.findall(r'"([^"]*)"', am.group(2)):
            mappings.append((method, path_regex(base + p), base + p, f.name))
    views.update(v for v in re.findall(r'return\s+"((?!redirect:)[a-zA-Z_/]+)"', src) if not v.startswith("/"))
    views.update(re.findall(r'private static final String \w+_VIEW = "([^"]+)"', src))
mappings.append(("POST", path_regex("/logout"), "/logout", "Spring Security"))
mappings.append(("POST", path_regex("/dologin"), "/dologin", "Spring Security"))

def mapped(method, url):
    return any((m == method or m == "ANY") and rx.match(url) for m, rx, _, _ in mappings)

# ---------------------------------------------------------------- 2. every URL used by the templates
STATIC = ("/css/", "/js/", "/img/", "/vendor/", "/fonts/", "/user-photos/")
def norm(u):
    u = re.sub(r"\([^)]*\)", "", u)           # (id=${...})
    u = re.sub(r"\{[^/}]*\}", "x", u)
    return u.split("?")[0].split("#")[0]

for t in sorted(tpl.rglob("*.html")):
    s = read(t); rel = t.relative_to(tpl)
    # forms
    for fm in re.finditer(r'<form\b[^>]*>', s):
        tag = fm.group(0)
        a = re.search(r'th:action="@\{([^"]*)\}"', tag)
        if not a: continue
        forms_seen[0] += 1
        method = (re.search(r'method="(\w+)"', tag) or [0, "get"])[1].upper()
        url = norm(a.group(1))
        ok(mapped(method, url), f"{rel}: form {method} {a.group(1)} has no matching controller mapping")
    # links
    for a in re.finditer(r'th:href="@\{([^"]*?)\}(?:\s*\+[^"]*)?"', read(t)):
        url = norm(a.group(1))
        if url.startswith(STATIC) or not url.startswith("/"): continue
        ok(mapped("GET", url), f"{rel}: link GET {a.group(1)} has no matching controller mapping")
    # no destructive GET links
    ok(not re.search(r'th:href="@\{/user/(delete|account)', s), f"{rel}: destructive action linked with GET")
    # old stack leftovers
    ok("fa fa-" not in s and "cdn.jsdelivr" not in s and "unpkg.com" not in s and "swal(" not in s, f"{rel}: leftover CDN / FontAwesome / SweetAlert v1")

# ---------------------------------------------------------------- 3. view names -> templates
for v in sorted(views):
    ok((tpl / (v + ".html")).exists(), f"controller returns view '{v}' but templates/{v}.html does not exist")

# ---------------------------------------------------------------- 4. fragments exist
frag_defs = {}
for t in tpl.rglob("*.html"):
    for m in re.finditer(r'th:fragment="(\w+)', read(t)):
        frag_defs[(str(t.relative_to(tpl))[:-5], m.group(1))] = True
for t in sorted(tpl.rglob("*.html")):
    s = read(t)
    for m in re.finditer(r'~\{([\w/]+) :: (\w+)', s):
        ok((m.group(1), m.group(2)) in frag_defs, f"{t.relative_to(tpl)}: fragment {m.group(1)} :: {m.group(2)} not defined")
    for m in re.finditer(r'th:replace="~\{([\w/]+) :: Layout', s):
        ok((m.group(1), "Layout") in frag_defs, f"{t.relative_to(tpl)}: layout {m.group(1)} missing")

# ---------------------------------------------------------------- 5. th:field names exist on the form object
def props(cls):
    src = read(java / "dto" / f"{cls}.java") if (java / "dto" / f"{cls}.java").exists() else read(java / "entity" / f"{cls}.java")
    return set(re.findall(r'(?:private|public)\s+(?:String|int|boolean)\s+(\w+)\s*;', src))
forms = {"normal/add_contact_form": "ContactForm", "normal/contact_fields": "ContactForm", "normal/update_form": "ContactForm",
         "normal/profile_edit": "ProfileForm", "normal/setting": "ChangePasswordForm", "signup": "User"}
binder = set(re.findall(r'"(\w+)"', re.search(r'setAllowedFields\(([^)]*)\)', read(java / "controller/UserController.java")).group(1)))
for name, cls in forms.items():
    s = read(tpl / (name + ".html")); p = props(cls)
    for fld in set(re.findall(r'th:field="\*\{(\w+)\}"', s)):
        ok(fld in p, f"{name}: th:field '{fld}' is not a property of {cls}")
        if cls == "User": ok(fld in binder, f"{name}: '{fld}' is not in the registration binder whitelist {sorted(binder)}")
    for fld in set(re.findall(r"hasErrors\('(\w+)'\)", s)):
        ok(fld in p, f"{name}: hasErrors('{fld}') is not a property of {cls}")
ok("id" not in binder and "role" not in binder and "enabled" not in binder, "registration binder must not allow id/role/enabled")

# ---------------------------------------------------------------- 6. request parameters used by controllers are present in the forms
def has_input(file, name):
    return re.search(r'name="%s"' % re.escape(name), read(tpl / file)) is not None or re.search(r'th:name="\$\{?\'?%s' % name, read(tpl / file)) is not None or ("'%s'" % name) in read(tpl / file)
for file, params in {"login.html": ["username", "password", "remember-me"], "forgot_email_form.html": ["email"], "verify_otp.html": ["otp"],
                     "change_password.html": [], "signup.html": ["agreement"],
                     "normal/contact_fields.html": ["profileImage"], "normal/profile_edit.html": ["profileImage"],
                     "normal/setting.html": ["currentPassword"], "normal/base.html": ["q"]}.items():
    for p in params: ok(has_input(file, p), f"{file}: expected an input named '{p}'")
ok("'newPassword'" in read(tpl / "change_password.html") and "'confirmPassword'" in read(tpl / "change_password.html"), "change_password.html: newPassword/confirmPassword inputs")
ok('enctype="multipart/form-data"' in read(tpl / "normal/add_contact_form.html") and 'enctype="multipart/form-data"' in read(tpl / "normal/update_form.html")
   and 'enctype="multipart/form-data"' in read(tpl / "normal/profile_edit.html"), "upload forms need enctype=multipart/form-data")

# ---------------------------------------------------------------- 7. JavaScript wiring
js = read(res / "static/js/script.js")
all_tpl = "\n".join(read(t) for t in tpl.rglob("*.html"))
defined = set(re.findall(r'function\s+(\w+)\s*\(', js)) | set(re.findall(r'(?:const|let|var)\s+(\w+)\s*=\s*(?:async\s*)?function', js))
for call in set(re.findall(r'on(?:click|input|error)="(\w+)\(', all_tpl)):
    ok(call in defined, f"templates call {call}() but script.js does not define it")
for dom_id in ["menu-toggle", "sidebar-overlay", "global-search", "search-suggestions", "payment_field", "donate-btn", "donation-status"]:
    ok(f'id="{dom_id}"' in all_tpl, f"script.js uses #{dom_id} but no template defines it")
for hook in ["data-toggle-password", "data-confirm", "data-preview", "data-match"]:
    ok(hook in all_tpl and hook in js, f"hook {hook} must appear in both templates and script.js")
for url in set(re.findall(r"""fetch\(\s*'(/[^']*)'""", js)):
    base = url.rstrip("/")
    probe = url + "x" if url.endswith("/") else base
    ok(any(rx.match(probe) for m, rx, _, _ in mappings if m in ("GET", "POST", "ANY")), f"script.js fetches {url} which has no controller mapping")
ok(len(re.findall(r"function paymentStart", js)) == 1, "paymentStart must be defined exactly once (no duplicated Razorpay init)")
ok("localhost" not in js, "script.js contains a hard-coded localhost URL")
ok(not re.search(r"x``", js), "script.js still contains the stray x`` token")
for token in ["razorpay_signature", "/user/create_order", "/user/update_order", "/user/payment_failed"]:
    ok(token in js, f"donation JS lost {token}")
ok(not re.search(r"key_secret|rzp_(test|live)_[A-Za-z0-9]{6,}", js + all_tpl), "a Razorpay key appears in front-end code")

# ---------------------------------------------------------------- 8. security hygiene
cfg = read(java / "config/MyConfig.java")
ok("csrf.disable" not in cfg and ".csrf(csrf -> csrf.ignoringRequestMatchers(\"/razorpay/webhook\"))" in cfg, "CSRF must stay enabled (only the signed webhook is excluded)")
props_txt = read(res / "application.properties")
ok(not re.search(r"password\s*=\s*[^$\s][^\s]*", props_txt.replace("password=${", "")), "a literal password is present in application.properties")
ok(not re.search(r"\.csrf\(.*disable", cfg), "csrf disabled")
for f in java.rglob("*.java"):
    s = read(f)
    ok("System.out" not in s and "printStackTrace" not in s, f"{f.relative_to(java)}: System.out / printStackTrace")

print(f"{checks - len(problems)} of {checks} checks passed ({forms_seen[0]} forms verified)")
for p in problems: print("  PROBLEM:", p)
sys.exit(1 if problems else 0)
