package project_manager.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import project_manager.service.MyDayService;
import project_manager.web.dto.MyDayResponse;

@RestController
public class MyDayController {
    private final MyDayService service;

    public MyDayController(MyDayService service) {
        this.service = service;
    }

    @GetMapping("/api/my-day")
    public ResponseEntity<MyDayResponse> read() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.read());
    }
}
