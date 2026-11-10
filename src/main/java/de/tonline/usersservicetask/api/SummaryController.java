package de.tonline.usersservicetask.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import de.tonline.usersservicetask.service.SummaryService;

@RestController
public class SummaryController {

    private final SummaryService service;

    public SummaryController(SummaryService service) {
        this.service = service;
    }

    @GetMapping("/users/{id}/summary")
    public SummaryResponse summary(@PathVariable long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be a positive integer");
        }
        return service.getSummary(id);
    }
}
