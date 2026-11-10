package de.tonline.usersservicetask.api;

import de.tonline.usersservicetask.api.dto.SummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import de.tonline.usersservicetask.service.SummaryService;

@RestController
public class SummaryController {

    private static final Logger log = LoggerFactory.getLogger(SummaryController.class);

    private final SummaryService service;

    public SummaryController(SummaryService service) {
        this.service = service;
    }

    @GetMapping("/users/{id}/summary")
    public SummaryResponse summary(@PathVariable long id, HttpServletRequest request) {
        log.info("Incoming summary call: {} {}", request.getMethod(), request.getRequestURI());
        if (id <= 0) {
            throw new IllegalArgumentException("id must be a positive integer");
        }
        return service.getSummary(id);
    }
}
