package com.ethanstore.api.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static com.ethanstore.api.constant.Authority.*;

@Getter
@AllArgsConstructor
public enum Role {
    ROLE_USER(USER_AUTHORITIES),
    ROLE_HR(HR_AUTHORITIES),
    ROLE_MANAGER(MANAGER_AUTHORITIES),
    ROLE_ADMIN(ADMIN_AUTHORITIES),
    ROLE_SUPER_ADMIN(SUPER_ADMIN_AUTHORITIES);

    private String[] authorities;
}
