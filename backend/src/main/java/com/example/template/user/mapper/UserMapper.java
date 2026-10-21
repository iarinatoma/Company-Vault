package com.example.template.user.mapper;

import com.example.template.user.dto.UserResponse;
import com.example.template.user.model.User;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);
}
