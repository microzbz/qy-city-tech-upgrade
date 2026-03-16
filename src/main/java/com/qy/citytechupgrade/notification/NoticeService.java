package com.qy.citytechupgrade.notification;

import com.qy.citytechupgrade.common.dto.PagedResult;
import com.qy.citytechupgrade.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {
    private final SysNoticeRepository sysNoticeRepository;

    public void push(Long userId, String title, String content) {
        SysNotice n = new SysNotice();
        n.setUserId(userId);
        n.setTitle(title);
        n.setContentText(content);
        n.setReadFlag(false);
        sysNoticeRepository.save(n);
    }

    public PagedResult<SysNotice> myNotices(Long userId, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);
        Page<SysNotice> result = sysNoticeRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PagedResult.of(result.getContent(), result.getTotalElements(), result.getNumber() + 1, result.getSize());
    }

    public void markRead(Long noticeId, Long userId) {
        SysNotice n = sysNoticeRepository.findById(noticeId).orElseThrow(() -> new BizException("消息不存在"));
        if (!n.getUserId().equals(userId)) {
            throw new BizException("无权限操作该消息");
        }
        n.setReadFlag(true);
        sysNoticeRepository.save(n);
    }
}
