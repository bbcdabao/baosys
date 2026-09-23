package baosys.manager.infra.database.mapper;

import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import baosys.manager.api.enums.SystemType;
import baosys.manager.infra.database.entity.UserInfoEntity;

public interface UserInfoMapper extends BaseMapper<UserInfoEntity> {

    default Optional<UserInfoEntity> findById(Long id) {
        return Optional.ofNullable(selectOne(Wrappers.<UserInfoEntity>lambdaQuery()
                .eq(UserInfoEntity::getId, id)));
    }

    default Optional<UserInfoEntity> findByIdForUpdate(Long id) {
        return Optional.ofNullable(selectOne(Wrappers.<UserInfoEntity>lambdaQuery()
                .eq(UserInfoEntity::getId, id)
                .last("FOR UPDATE")));
    }

    default Optional<UserInfoEntity> findByUserNameAndSystemType(
            String userName,
            SystemType systemType) {
        return Optional.ofNullable(selectOne(Wrappers.<UserInfoEntity>lambdaQuery()
                .eq(UserInfoEntity::getUserName, userName)
                .eq(UserInfoEntity::getSystemType, systemType)));
    }

    default Optional<UserInfoEntity> findByUserNameAndSystemTypeForUpdate(
            String userName,
            SystemType systemType) {
        return Optional.ofNullable(selectOne(Wrappers.<UserInfoEntity>lambdaQuery()
                .eq(UserInfoEntity::getUserName, userName)
                .eq(UserInfoEntity::getSystemType, systemType)
                .last("FOR UPDATE")));
    }

    default boolean existsByUserNameAndSystemType(String userName, SystemType systemType) {
        return selectCount(Wrappers.<UserInfoEntity>lambdaQuery()
                .eq(UserInfoEntity::getUserName, userName)
                .eq(UserInfoEntity::getSystemType, systemType)) > 0;
    }

    default boolean existsByUserNameAndSystemTypeExcludingId(
            String userName,
            SystemType systemType,
            Long id) {
        return selectCount(Wrappers.<UserInfoEntity>lambdaQuery()
                .eq(UserInfoEntity::getUserName, userName)
                .eq(UserInfoEntity::getSystemType, systemType)
                .ne(UserInfoEntity::getId, id)) > 0;
    }

    default IPage<UserInfoEntity> selectPageByNameAndSystemType(
            String name,
            SystemType systemType,
            int page,
            int size) {
        var query = Wrappers.<UserInfoEntity>lambdaQuery()
                .orderByAsc(UserInfoEntity::getId);
        if (name != null && !name.isBlank()) {
            query.like(UserInfoEntity::getUserName, name.trim());
        }
        if (systemType != null) {
            query.eq(UserInfoEntity::getSystemType, systemType);
        }
        return selectPage(Page.of(page + 1L, size), query);
    }
}
