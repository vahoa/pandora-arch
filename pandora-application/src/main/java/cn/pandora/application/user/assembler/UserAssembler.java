package cn.pandora.application.user.assembler;

import cn.pandora.application.user.dto.UserDTO;
import cn.pandora.domain.user.User;

/**
 * 用户领域对象与DTO之间的转换器
 */
public class UserAssembler {

    private UserAssembler() {
    }

    public static UserDTO toDTO(User user) {
        if (user == null) {
            return null;
        }
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setStatus(user.getStatus().name());
        dto.setCreateTime(user.getCreateTime());
        return dto;
    }
}
