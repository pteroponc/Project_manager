package project_manager.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectMutationError(String code, String error, Map<String, String> fieldErrors,
                                   Long currentVersion) { }
