package com.lovemaptually.controller;
import com.lovemaptually.auth.CurrentUser;import com.lovemaptually.common.*;import com.lovemaptually.dto.request.*;import com.lovemaptually.dto.response.*;import com.lovemaptually.service.GroupService;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.http.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class GroupController {private final GroupService service;
 @PostMapping("/groups") ResponseEntity<ApiResponse<GroupResponse>> create(Authentication a,@Valid @RequestBody CreateGroupRequest b){return ResponseEntity.status(201).body(ApiResponse.of(201,"그룹을 만들었습니다",service.create(CurrentUser.id(a),b)));}
 @GetMapping("/groups/me") ApiResponse<GroupListResponse> mine(Authentication a){return ApiResponse.of(200,"조회했습니다",service.mine(CurrentUser.id(a)));}
 @PostMapping("/groups/{gid}/invites") ResponseEntity<ApiResponse<InviteResponse>> invite(Authentication a,@PathVariable long gid,@Valid @RequestBody CreateInviteRequest b){return ResponseEntity.status(201).body(ApiResponse.of(201,"초대 코드를 발급했습니다",service.invite(CurrentUser.id(a),gid,b)));}
 @GetMapping("/invites/{code}") ApiResponse<InvitePreviewResponse> inspect(@PathVariable String code){return ApiResponse.of(200,"조회했습니다",service.inspect(code));}
 @PostMapping("/groups/members") ResponseEntity<ApiResponse<GroupResponse>> join(Authentication a,@Valid @RequestBody JoinGroupRequest b){return ResponseEntity.status(201).body(ApiResponse.of(201,"그룹에 참여했습니다",service.join(CurrentUser.id(a),b)));}}
