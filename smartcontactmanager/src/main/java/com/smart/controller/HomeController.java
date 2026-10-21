package com.smart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.smart.entity.User;

@Controller
public class HomeController {

	@RequestMapping("/")
	public String home(Model model) {
		model.addAttribute("title", "Home-SmartContactManager");
		return "home";
	}
	@RequestMapping("/about")
	public String about(Model model) {
		model.addAttribute("title", "About-SmartContactManager");
		return "about";
	}
	@RequestMapping("/signup")
	public String signup(Model model) {
		model.addAttribute("title", "Register-SmartContactManager");
		model.addAttribute("user", new User());
		return "signup";
	}
	@GetMapping("/signin")
	public String signin() {
	    return "login";
	}
}
