package com.qy.citytechupgrade.notification;

import com.qy.citytechupgrade.common.exception.BizException;
import lombok.RequiredArgsConstructor;
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

    public List<SysNotice> myNotices(Long userId) {
        return sysNoticeRepository.findByUserIdOrderByCreatedAtDesc(userId);
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
