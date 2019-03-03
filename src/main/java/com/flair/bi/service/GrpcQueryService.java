package com.flair.bi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flair.bi.domain.Datasource;
import com.flair.bi.domain.DatasourceConstraint;
import com.flair.bi.domain.property.CheckboxProperty;
import com.flair.bi.domain.visualmetadata.VisualMetadata;
import com.flair.bi.messages.Query;
import com.flair.bi.messages.QueryResponse;
import com.flair.bi.messages.QueryValidationResponse;
import com.flair.bi.messages.RunQueryResponse;
import com.flair.bi.service.dto.RunQueryResponseDTO;
import com.flair.bi.web.rest.dto.QueryValidationResponseDTO;
import com.flair.bi.web.rest.errors.EntityNotFoundException;
import com.flair.bi.web.rest.util.QueryGrpcUtils;
import com.flair.bi.web.websocket.FbEngineWebSocketService;
import com.project.bi.query.dto.ConditionExpressionDTO;
import com.project.bi.query.dto.QueryDTO;
import com.project.bi.query.expression.condition.ConditionExpression;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GrpcQueryService {

    private final DatasourceService datasourceService;
    private final DatasourceConstraintService datasourceConstraintService;
    private final FbEngineWebSocketService fbEngineWebSocketService;
    private final IGrpcService grpcService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RunQueryResponseDTO sendRunQuery(QueryDTO queryDTO, Datasource datasource) {
        queryDTO.setSource(datasource.getName());

        log.debug("Sending run query request for datasource {} id {}",
            queryDTO.getSource(), datasource.getConnectionName());

        Query query = QueryGrpcUtils.mapToQuery(queryDTO, datasource.getConnectionName(), null, null);

        RunQueryResponse result;
        try {
            result = grpcService.runQuery(query, queryDTO.isMetaRetrieved());
        } catch (StatusRuntimeException e) {
            log.error("Error running a query {}", queryDTO, e);
            return new RunQueryResponseDTO();
        }

        String resultString = result.getResult();

        log.debug("Sending run query result {}", resultString);

        Map<String, Object> map = Optional.ofNullable(resultString)
            .filter(r -> StringUtils.isNotEmpty(resultString))
            .map(r -> {
                try {
                    return (Map<String, Object>)objectMapper.readValue(resultString, Map.class);
                } catch (IOException e) {
                    log.error("Error parsing run query result for query {}", queryDTO, e);
                    return null;
                }
            })
            .orElse(null);

        return new RunQueryResponseDTO()
            .setResult(map);
    }

    public QueryValidationResponseDTO sendValidateQuery(Long datasourceId, QueryDTO queryDTO, String visualMetadataId,
                                                        ConditionExpression conditionExpression, String userId) {
        Datasource datasource = getDatasource(datasourceId);

        Optional.ofNullable(conditionExpression)
            .map(x -> {
                ConditionExpressionDTO dto = new ConditionExpressionDTO();
                dto.setSourceType(ConditionExpressionDTO.SourceType.BASE);
                dto.setConditionExpression(x);
                return dto;
            })
            .ifPresent(queryDTO.getConditionExpressions()::add);

        DatasourceConstraint constraint = datasourceConstraintService.findByUserAndDatasource(userId, datasource.getId());
        Optional.ofNullable(constraint)
            .map(DatasourceConstraint::build)
            .ifPresent(queryDTO.getConditionExpressions()::add);

        queryDTO.setSource(datasource.getName());

        Query query = QueryGrpcUtils.mapToQuery(queryDTO,
            datasource.getConnectionName(),
            visualMetadataId != null ? visualMetadataId : "",
            userId);

        log.debug("Invoking gRPC query {}", query);
        QueryValidationResponse queryResponse = grpcService.validate(query);
        log.debug("Received gRPC response {}", queryResponse);

        return new QueryValidationResponseDTO()
            .setValidationResultType(queryResponse.getValidationResult().getType().name())
            .setRawQuery(queryResponse.getRawQuery())
            .setError(queryResponse.getValidationResult().getData());
    }


    private Datasource getDatasource(Long datasourceId) {
        return Optional.ofNullable(datasourceService.findOne(datasourceId))
            .orElseThrow(() -> new EntityNotFoundException("Datasource with id " + datasourceId + " not found"));
    }

	public void sendGetDataStream(Long datasourcesId, String userId, VisualMetadata visualMetadata, QueryDTO queryDTO, String visualMetadataId) throws InterruptedException {
        Datasource datasource = getDatasource(datasourcesId);

        DatasourceConstraint constraint = datasourceConstraintService.findByUserAndDatasource(userId, datasource.getId());

        Optional.ofNullable(visualMetadata)
            .map(VisualMetadata::getConditionExpression)
            .map(x -> {
                ConditionExpressionDTO dto = new ConditionExpressionDTO();
                dto.setSourceType(ConditionExpressionDTO.SourceType.BASE);
                dto.setConditionExpression(x);
                return dto;
            })
            .ifPresent(queryDTO.getConditionExpressions()::add);

        Optional.ofNullable(constraint)
            .map(DatasourceConstraint::build)
            .ifPresent(queryDTO.getConditionExpressions()::add);

        queryDTO.setSource(datasource.getName());


        Optional<CheckboxProperty> enableCaching = Optional.ofNullable(visualMetadata)
            .map(VisualMetadata::getProperties)
            .filter(p -> p instanceof CheckboxProperty).map(p -> (CheckboxProperty) p)
            .filter(p -> p.isValue() && p.getPropertyType().getName().equalsIgnoreCase("Enable caching"));
        if (enableCaching.isPresent()) {
            // TODO - Need to replace below call with caching engine call
            if (visualMetadata != null) {
                callGrpcBiDirectionalAndPushInSocket(datasource, queryDTO, visualMetadata.getId(), "vizualization", userId);
            } else {
                callGrpcBiDirectionalAndPushInSocket(datasource, queryDTO, visualMetadataId, "filters", userId);
            }
            //callGrpcBiDirectionalAndPushInSocketForFilters(datasource, queryDTO,fbiEngineDTO.getvId());


        } else {
            if (visualMetadata != null) {
                callGrpcBiDirectionalAndPushInSocket(datasource, queryDTO, visualMetadata.getId(), "vizualization", userId);
            } else {
                callGrpcBiDirectionalAndPushInSocket(datasource, queryDTO, visualMetadataId, "filters", userId);
            }
            //callGrpcBiDirectionalAndPushInSocketForFilters(datasource, queryDTO,fbiEngineDTO.getvId());
        }
    }
    
    public void sendGetDataStream(String sourcesId, String userId, QueryDTO queryDTO) throws InterruptedException {
    	 callGrpcBiDirectionalAndPushInSocket(sourcesId,queryDTO,getVid(userId),userId);
    }
    
    private String getVid(String userId){
		StringBuilder vId= new StringBuilder();
		vId.append(userId).append("-").append(new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime()));
		return vId.toString();
    }
    
    
    private  void callGrpcBiDirectionalAndPushInSocket(String sourcesId, QueryDTO queryDTO, String vId, String userId) throws InterruptedException {
        Query query = QueryGrpcUtils.mapToQuery(queryDTO, sourcesId, vId, userId);
        StreamObserver<QueryResponse> responseObserver = new StreamObserver<QueryResponse>() {
            @Override
            public void onNext(QueryResponse queryResponse) {
                log.debug("Finished trip with===" + queryResponse.toString());
                fbEngineWebSocketService.pushGRPCMetaDeta(queryResponse);
            }

            @Override
            public void onError(Throwable t) {
                log.error("callGrpcBiDirectionalAndPushInSocket Failed:", t);
                if (t instanceof StatusRuntimeException) {
                    StatusRuntimeException statusRuntimeException = (StatusRuntimeException) t;
                    fbEngineWebSocketService.pushGRPCMetaDataError(userId, statusRuntimeException.getStatus());
                }
            }

            @Override
            public void onCompleted() {
                log.debug("Finished Request");
            }
        };

        StreamObserver<Query> requestObserver = grpcService.getDataStream(responseObserver);
        try {
            requestObserver.onNext(query);
        } catch (RuntimeException e) {
            // Cancel RPC
            requestObserver.onError(e);
            throw e;
        }
        // Mark the end of requests
        requestObserver.onCompleted();

    }
    

    private void callGrpcBiDirectionalAndPushInSocket(Datasource datasource, QueryDTO queryDTO, String vId, String request, String userId) throws InterruptedException {
        Query query = QueryGrpcUtils.mapToQuery(queryDTO, datasource.getConnectionName(), vId, userId);
        StreamObserver<QueryResponse> responseObserver = new StreamObserver<QueryResponse>() {
            @Override
            public void onNext(QueryResponse queryResponse) {
                log.debug("Finished trip with===" + queryResponse.toString());
                fbEngineWebSocketService.pushGRPCMetaDeta(queryResponse, request);
            }

            @Override
            public void onError(Throwable t) {
                log.error("callGrpcBiDirectionalAndPushInSocket Failed:", t);
                if (t instanceof StatusRuntimeException) {
                    StatusRuntimeException statusRuntimeException = (StatusRuntimeException) t;
                    fbEngineWebSocketService.pushGRPCMetaDataError(userId, statusRuntimeException.getStatus());
                }
            }

            @Override
            public void onCompleted() {
                log.debug("Finished Request");
            }
        };

        StreamObserver<Query> requestObserver = grpcService.getDataStream(responseObserver);
        try {
            requestObserver.onNext(query);
        } catch (RuntimeException e) {
            // Cancel RPC
            requestObserver.onError(e);
            throw e;
        }
        // Mark the end of requests
        requestObserver.onCompleted();

    }

}