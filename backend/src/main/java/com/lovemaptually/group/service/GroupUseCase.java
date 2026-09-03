package com.lovemaptually.group.service;

import com.lovemaptually.group.dto.request.CreateGroupRequest;
import com.lovemaptually.group.dto.response.GroupResponse;
import com.lovemaptually.group.dto.response.MyGroupsResponse;
import com.lovemaptually.invite.dto.request.CreateInviteRequest;
import com.lovemaptually.invite.dto.response.InvitePreviewResponse;
import com.lovemaptually.invite.dto.response.InviteResponse;

public interface GroupUseCase {

    GroupResponse createGroup(Long userId, CreateGroupRequest request);

    MyGroupsResponse getMyGroups(Long userId);

    InviteResponse createInvite(Long userId, Long groupId, CreateInviteRequest request);

    InvitePreviewResponse previewInvite(String code);

    GroupResponse joinGroup(Long userId, String inviteCode);
}
