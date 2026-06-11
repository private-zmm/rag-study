package com.ragstudy.auth.convert;

import com.ragstudy.auth.controller.dto.UserDto;
import com.ragstudy.auth.dal.dataobject.UserEntity;

public final class AuthConvert {

    private AuthConvert() {
    }

    public static UserDto toDto(UserEntity user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getNickname());
    }
}
