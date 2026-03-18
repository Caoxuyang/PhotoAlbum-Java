package com.photoalbum.controller;

import com.photoalbum.model.Photo;
import com.photoalbum.service.PhotoService;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Controller for displaying a single photo in full size
 */
@Controller
@RequestMapping("/detail")
public class DetailController {

    private static final Logger logger = LoggerFactory.getLogger(DetailController.class);

    private final PhotoService photoService;

    public DetailController(PhotoService photoService) {
        this.photoService = photoService;
    }

    /**
     * Handles GET requests to display a photo
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        if (id == null || id.trim().isEmpty()) {
            return "redirect:/";
        }

        try {
            Optional<Photo> photoOpt = photoService.getPhotoById(id);
            if (!photoOpt.isPresent()) {
                return "redirect:/";
            }

            Photo photo = photoOpt.get();
            model.addAttribute("photo", photo);

            // Find previous and next photos for navigation
            Optional<Photo> previousPhoto = photoService.getPreviousPhoto(photo);
            Optional<Photo> nextPhoto = photoService.getNextPhoto(photo);

            model.addAttribute("previousPhotoId", previousPhoto.isPresent() ? previousPhoto.get().getId() : null);
            model.addAttribute("nextPhotoId", nextPhoto.isPresent() ? nextPhoto.get().getId() : null);

            return "detail";
        } catch (Exception ex) {
            logger.error("Error loading photo with ID {}", id, ex);
            return "redirect:/";
        }
    }

    /**
     * Handles POST requests to delete a photo
     */
    @PostMapping("/{id}/delete")
    public String deletePhoto(@PathVariable String id, RedirectAttributes redirectAttributes,
                              HttpServletRequest request) {
        String remoteIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        try {
            boolean deleted = photoService.deletePhoto(id);
            if (deleted) {
                logger.info("Photo {} deleted successfully [ip={}, userAgent={}]", id, remoteIp, userAgent);
                redirectAttributes.addFlashAttribute("successMessage", "Photo deleted successfully");
            } else {
                logger.warn("Delete requested for non-existent photo {} [ip={}, userAgent={}]", id, remoteIp, userAgent);
                redirectAttributes.addFlashAttribute("errorMessage", "Photo not found");
            }
        } catch (Exception ex) {
            logger.error("Error deleting photo {} [ip={}, userAgent={}]", id, remoteIp, userAgent, ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete photo. Please try again.");
        }
        return "redirect:/";
    }

    /**
     * Returns the real client IP address, taking into account reverse-proxy headers.
     * Note: X-Forwarded-For and X-Real-IP headers may be spoofed; in production
     * deployments, restrict access to these headers to trusted proxies only.
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For may contain a comma-separated list; take the first value
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}