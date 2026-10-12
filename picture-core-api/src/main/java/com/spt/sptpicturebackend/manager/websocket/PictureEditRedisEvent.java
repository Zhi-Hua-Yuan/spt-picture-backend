package com.spt.sptpicturebackend.manager.websocket;

import com.spt.sptpicturebackend.manager.websocket.model.PictureEditResponseMessage;
import lombok.Data;

@Data
public class PictureEditRedisEvent {
    private Long pictureId;
    private String originPodId;
    private String excludeSessionId;
    private PictureEditResponseMessage message;
}
