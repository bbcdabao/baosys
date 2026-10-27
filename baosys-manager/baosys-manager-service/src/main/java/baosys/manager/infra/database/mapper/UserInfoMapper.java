package baosys.manager.infra.database.mapper;

import baosys.manager.infra.database.entity.UserInfoEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Optional;
import org.springframework.util.StringUtils;

public interface UserInfoMapper extends BaseMapper<UserInfoEntity> {
    default Optional<UserInfoEntity> findById(Long id) {
        return Optional.ofNullable(
            selectOne(Wrappers.<UserInfoEntity>lambdaQuery().eq(UserInfoEntity::getId, id))
        );
    }

    default Optional<UserInfoEntity> findByIdForUpdate(Long id) {
        return Optional.ofNullable(
            selectOne(
                Wrappers.<UserInfoEntity>lambdaQuery()
                    .eq(UserInfoEntity::getId, id)
                    .last("FOR UPDATE")
            )
        );
    }

    default Optional<UserInfoEntity> findByUserName(String userName) {
        return Optional.ofNullable(
            selectOne(
                Wrappers.<UserInfoEntity>lambdaQuery().eq(UserInfoEntity::getUserName, userName)
            )
        );
    }

    default Optional<UserInfoEntity> findByUserNameForUpdate(String userName) {
        return Optional.ofNullable(
            selectOne(
                Wrappers.<UserInfoEntity>lambdaQuery()
                    .eq(UserInfoEntity::getUserName, userName)
                    .last("FOR UPDATE")
            )
        );
    }

    default boolean existsByUserName(String userName) {
        return (
            selectCount(
                Wrappers.<UserInfoEntity>lambdaQuery().eq(UserInfoEntity::getUserName, userName)
            ) > 0
        );
    }

    default boolean existsByUserNameExcludingId(String userName, Long id) {
        return (
            selectCount(
                Wrappers.<UserInfoEntity>lambdaQuery()
                    .eq(UserInfoEntity::getUserName, userName)
                    .ne(UserInfoEntity::getId, id)
            ) > 0
        );
    }

    default IPage<UserInfoEntity> selectPageByName(
        String name,
        String systemType,
        int page,
        int size
    ) {
        var query = Wrappers.<UserInfoEntity>lambdaQuery().orderByAsc(UserInfoEntity::getId);
        if (StringUtils.hasText(name)) {
            query.like(UserInfoEntity::getUserName, name.trim());
        }
        if (systemType != null) {
            query.apply(
                "{0} = ANY(string_to_array(user_permissions, ','))",
                systemType
            );
        }
        return selectPage(Page.of(page + 1L, size), query);
    }
}
