package cn.pandora.infrastructure.persistence.converter;

import cn.pandora.domain.user.User;
import cn.pandora.domain.user.UserStatus;
import cn.pandora.infrastructure.persistence.user.UserDO;

/**
 * 用户领域模型与数据对象之间的转换器
 */
public class UserConverter {

    private UserConverter() {
    }

    public static UserDO toDataObject(User user) {
        if (user == null) {
            return null;
        }
        UserDO dataObject = new UserDO();
        dataObject.setId(user.getId());
        dataObject.setUsername(user.getUsername());
        dataObject.setPassword(user.getPassword());
        dataObject.setEmail(user.getEmail());
        dataObject.setPhone(user.getPhone());
        dataObject.setStatus(user.getStatus().getCode());
        dataObject.setCreateTime(user.getCreateTime());
        dataObject.setUpdateTime(user.getUpdateTime());
        return dataObject;
    }

    public static User toDomainModel(UserDO dataObject) {
        if (dataObject == null) {
            return null;
        }
        return User.reconstruct(
                dataObject.getId(),
                dataObject.getUsername(),
                dataObject.getPassword(),
                dataObject.getEmail(),
                dataObject.getPhone(),
                UserStatus.fromCode(dataObject.getStatus()),
                dataObject.getCreateTime(),
                dataObject.getUpdateTime()
        );
    }
}
