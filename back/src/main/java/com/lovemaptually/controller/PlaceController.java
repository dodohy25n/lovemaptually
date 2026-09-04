package com.lovemaptually.controller;
import com.lovemaptually.auth.CurrentUser;import com.lovemaptually.common.ApiResponse;import com.lovemaptually.dto.request.AddGroupPlaceRequest;import com.lovemaptually.dto.response.*;import com.lovemaptually.service.PlaceService;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.http.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class PlaceController {private final PlaceService service;
 @GetMapping("/places") ApiResponse<PlaceSearchResponse> search(@RequestParam String query,@RequestParam(required=false)String region,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.of(200,"조회했습니다",service.search(query,region,page,size));}
 @GetMapping("/places/{id}") ApiResponse<PlaceDetailResponse> detail(@PathVariable long id){return ApiResponse.of(200,"조회했습니다",service.detail(id));}
 @PostMapping("/groups/{gid}/places") ResponseEntity<ApiResponse<GroupPlaceResponse>> add(Authentication a,@PathVariable long gid,@Valid @RequestBody AddGroupPlaceRequest b){return ResponseEntity.status(201).body(ApiResponse.of(201,"우리 지도에 담았습니다",service.add(CurrentUser.id(a),gid,b)));}
 @GetMapping("/groups/{gid}/places") ApiResponse<GroupMapResponse> map(Authentication a,@PathVariable long gid,@RequestParam(required=false)String label){return ApiResponse.of(200,"조회했습니다",service.map(CurrentUser.id(a),gid,label));}
 @GetMapping("/groups/{gid}/places/{pid}") ApiResponse<PinDetailResponse> pin(Authentication a,@PathVariable long gid,@PathVariable long pid){return ApiResponse.of(200,"조회했습니다",service.pin(CurrentUser.id(a),gid,pid));}}
