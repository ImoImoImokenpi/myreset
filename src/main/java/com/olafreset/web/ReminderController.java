package com.olafreset.web;

import com.olafreset.domain.AppUser;
import com.olafreset.service.ReminderService;
import com.olafreset.service.ReminderService.Message;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** リマインダー通知の設定と、ブラウザ（sw.js）・外部 cron からの呼び出し。 */
@Controller
public class ReminderController {

    private final ReminderService reminders;
    private final String cronKey;

    public ReminderController(ReminderService reminders, @Value("${app.cron-key:}") String cronKey) {
        this.reminders = reminders;
        this.cronKey = cronKey;
    }

    @PostMapping("/api/push/subscribe")
    @ResponseBody
    ResponseEntity<Void> subscribe(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                                   @RequestParam String endpoint) {
        if (!endpoint.startsWith("https://")) {
            return ResponseEntity.badRequest().build();
        }
        reminders.subscribe(me, endpoint);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/push/unsubscribe")
    @ResponseBody
    ResponseEntity<Void> unsubscribe(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                                     @RequestParam String endpoint) {
        reminders.unsubscribe(me, endpoint);
        return ResponseEntity.noContent().build();
    }

    /** 通知を受け取ったブラウザが、表示する内容を取りに来る。 */
    @GetMapping("/api/reminder")
    @ResponseBody
    Message message(@ModelAttribute(name = "currentUser", binding = false) AppUser me) {
        return reminders.message(me, LocalDate.now());
    }

    @PostMapping("/account/reminder")
    String setTime(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                   @RequestParam(defaultValue = "") String time, RedirectAttributes ra) {
        if (time.isBlank()) {
            reminders.setTime(me, null, LocalDateTime.now());
            ra.addFlashAttribute("message", "リマインダーをオフにしました");
            return "redirect:/account";
        }
        try {
            LocalTime parsed = LocalTime.parse(time);
            reminders.setTime(me, parsed, LocalDateTime.now());
            ra.addFlashAttribute("message", "毎日 " + parsed + " に未達成の習慣を通知します");
        } catch (DateTimeParseException e) {
            ra.addFlashAttribute("message", "時刻の形式が正しくありません");
        }
        return "redirect:/account";
    }

    @PostMapping("/account/reminder/test")
    String test(@ModelAttribute(name = "currentUser", binding = false) AppUser me, RedirectAttributes ra) {
        if (reminders.deviceCount(me) == 0) {
            ra.addFlashAttribute("message", "通知を受け取る端末が登録されていません。先に「この端末で通知を受け取る」を押してください");
            return "redirect:/account";
        }
        int sent = reminders.sendTest(me);
        ra.addFlashAttribute("message", sent > 0
                ? "テスト通知を送りました（" + sent + "台）"
                : "テスト通知を送れませんでした。しばらくしてからもう一度お試しください");
        return "redirect:/account";
    }

    /**
     * 外部の cron サービスから定期的に呼ぶ。Render の無料枠で眠っているサーバーを起こし、
     * リマインダーを送る。CRON_KEY が一致しないときは何もしない。
     */
    @GetMapping("/cron/reminders")
    @ResponseBody
    ResponseEntity<String> cron(@RequestParam(defaultValue = "") String key) {
        if (cronKey.isBlank() || !java.security.MessageDigest.isEqual(cronKey.getBytes(), key.getBytes())) {
            return ResponseEntity.notFound().build();
        }
        int sent = reminders.sendDue(LocalDateTime.now());
        return ResponseEntity.ok("sent=" + sent);
    }
}
