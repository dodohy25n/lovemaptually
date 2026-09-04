package com.lovemaptually.dto.response;import java.util.List;public record PlaceSearchResponse(List<PlaceResponse> content,int page,int size,long totalElements,int totalPages){}
