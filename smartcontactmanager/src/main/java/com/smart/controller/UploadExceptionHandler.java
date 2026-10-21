package com.smart.controller;

import java.net.URI;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smart.helper.Message;

import jakarta.servlet.http.HttpServletRequest;

/**
 * An oversized upload is rejected by the servlet container before any controller runs,
 * so it needs a global handler. The user is sent back to the page they came from.
 */
@ControllerAdvice
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleTooLarge(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("message",
                new Message("That image is too large. Please choose one up to 2 MB.", "danger"));
        return "redirect:" + safeReturnPath(request.getHeader("Referer"));
    }

    /** Only same-site "/user/..." paths are allowed as a return target (no open redirect). */
    static String safeReturnPath(String referer) {
        try {
            if (referer != null) {
                String path = URI.create(referer).getPath();
                if (path != null && path.startsWith("/user/") && !path.contains("//") && !path.contains("..")) {
                    return path;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to the default
        }
        return "/user/index";
    }
}
