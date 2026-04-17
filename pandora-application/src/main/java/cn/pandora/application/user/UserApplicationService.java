package cn.pandora.application.user;

import cn.pandora.application.user.assembler.UserAssembler;
import cn.pandora.application.user.command.ChangePasswordCommand;
import cn.pandora.application.user.command.CreateUserCommand;
import cn.pandora.application.user.dto.UserDTO;
import cn.pandora.common.exception.BusinessException;
import cn.pandora.common.result.ResultCode;
import cn.pandora.domain.event.DomainEventPublisher;
import cn.pandora.domain.user.User;
import cn.pandora.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户应用服务 —— 编排领域逻辑，不包含业务规则
 */
@Service
public class UserApplicationService {

    private final UserRepository userRepository;
    private final DomainEventPublisher domainEventPublisher;

    public UserApplicationService(UserRepository userRepository,
                                  DomainEventPublisher domainEventPublisher) {
        this.userRepository = userRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public UserDTO createUser(CreateUserCommand command) {
        if (userRepository.existsByUsername(command.getUsername())) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS,
                    "用户名 [" + command.getUsername() + "] 已被注册");
        }
        if (userRepository.existsByEmail(command.getEmail())) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS,
                    "邮箱 [" + command.getEmail() + "] 已被注册");
        }

        User user = User.create(
                command.getUsername(),
                command.getPassword(),
                command.getEmail(),
                command.getPhone()
        );

        user = userRepository.save(user);

        domainEventPublisher.publishAll(user.getDomainEvents());
        user.clearDomainEvents();

        return UserAssembler.toDTO(user);
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        return UserAssembler.toDTO(user);
    }

    @Transactional(readOnly = true)
    public UserDTO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        return UserAssembler.toDTO(user);
    }

    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        User user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        user.changePassword(command.getOldPassword(), command.getNewPassword());
        userRepository.save(user);
    }

    @Transactional
    public void disableUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        user.disable();
        userRepository.save(user);
    }

    @Transactional
    public void enableUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        user.enable();
        userRepository.save(user);
    }
}
