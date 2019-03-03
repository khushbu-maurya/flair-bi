package com.flair.bi.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flair.bi.messages.QueryResponse;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import javax.inject.Inject;
import java.util.HashMap;

@Async
@Service
@RequiredArgsConstructor
public class FbEngineWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Send meta to users subscribed on channel "/user/exchange/metaData".
     * <p>
     * The message will be sent only to the user with the given username.
     */

    public void pushGRPCMetaDeta(QueryResponse queryResponse, String request) {
        HashMap<String, Object> header = new HashMap<>();
        header.put("queryId", queryResponse.getQueryId());
        header.put("userId", queryResponse.getUserId());
        header.put("request", request);
        messagingTemplate.convertAndSendToUser(queryResponse.getUserId(), "/exchange/metaData", queryResponse.getData(), header);
    }

    public void pushGRPCMetaDataError(String userId, Status status) {
        HashMap<String, Object> header = new HashMap<>();
        header.put("userId", userId);
        messagingTemplate.convertAndSendToUser(userId, "/exchange/metaDataError", status, header);
    }
    
    /**
     * Send meta to users subscribed on channel "/user/exchange/sampleMetaData".
     * <p>
     * The message will be sent only to the user with the given username.
     */
    public void pushGRPCMetaDeta(QueryResponse queryResponse) {
        HashMap<String, Object> header = new HashMap<>();
        header.put("queryId", queryResponse.getQueryId());
        header.put("userId", queryResponse.getUserId());
        messagingTemplate.convertAndSendToUser(queryResponse.getUserId(), "/exchange/sampleMetaData", queryResponse.getData(), header);
    }

}