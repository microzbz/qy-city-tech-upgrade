package com.qy.citytechupgrade.notification;

import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.dto.PagedResult;
import com.qy.citytechupgrade.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {
    private final NoticeService noticeService;

    @GetMapping("/my")
    public ApiResponse<PagedResult<SysNotice>> my(@RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(noticeService.myNotices(SecurityUtils.currentUserId(), page, size));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<String> read(@PathVariable Long id) {
        noticeService.markRead(id, SecurityUtils.currentUserId());
        return ApiResponse.success("已读", "OK");
    }
}
