package project_manager.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BoardOrderResponse(String projectId, String columnKey, Boolean changed,
                                 List<Card> cards) {
    public record Card(String id, int position, long version) { }
}
