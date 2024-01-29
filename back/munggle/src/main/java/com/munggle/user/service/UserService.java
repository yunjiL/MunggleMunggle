package com.munggle.user.service;

import com.munggle.user.dto.*;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

public interface UserService extends UserDetailsService {

    UserMyPageDto getUserMypage(Long id);

    UserProfileDto getUserProfile(Long id);

    List<UserListDto> getSearchPage(String keyword);

    void joinMember(UserCreateDto userCreateDto);

    void updateProfile(Long id, UpdateProfileDto updateProfileDto);

    void updatePassword(Long id, String newPassword);

    void deleteMember(Long id);
}
