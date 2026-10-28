package top.aameow.web.controller.common;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aameow.common.core.domain.AjaxResult;
@RestController
public class HealthController {
    @GetMapping("/health")
    public AjaxResult health() { return AjaxResult.success(); }
}
