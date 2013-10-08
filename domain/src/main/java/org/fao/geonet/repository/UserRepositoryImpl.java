package org.fao.geonet.repository;

import org.fao.geonet.domain.*;
import org.fao.geonet.utils.Log;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Tuple;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implementation for all {@link User} queries that cannot be automatically generated by Spring-data.
 *
 * @author Jesse
 */
public class UserRepositoryImpl implements UserRepositoryCustom {

    @PersistenceContext
    private EntityManager _entityManager;

    @Override
    public User findOne(String userId) {
        return _entityManager.find(User.class, Integer.valueOf(userId));
    }

    @Override
    public User findOneByEmail(String email) {

        // The following code uses the JPA Criteria API to build a query
        // that is essentially:
        //      Select * from Users where email in (SELECT
        CriteriaBuilder cb = _entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);

        query.where(cb.isMember(email, root.get(User_.emailAddresses)));
        final List<User> resultList = _entityManager.createQuery(query).getResultList();
        if (resultList.isEmpty()) {
            return null;
        }
        if (resultList.size() > 1) {
            Log.error(Constants.DOMAIN_LOG_MODULE, "The database is inconsistent.  There are multiple users with the email address: "+email);
        }
        return resultList.get(0);
    }

    @Override
    @Nonnull
    public List<Pair<Integer, User>> findAllByGroupOwnerNameAndProfile(@Nonnull Collection<Integer> metadataIds,
                                                                       @Nullable Profile profile, @Nullable Sort sort) {
        CriteriaBuilder cb = _entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createQuery(Tuple.class);

        Root<User> userRoot = query.from(User.class);
        Root<Metadata> metadataRoot = query.from(Metadata.class);
        Root<UserGroup> userGroupRoot = query.from(UserGroup.class);

        query.multiselect(metadataRoot.get(Metadata_.id), userRoot);

        Predicate metadataPredicate = metadataRoot.get(Metadata_.id).in(metadataIds);
        Predicate ownerPredicate = cb.equal(metadataRoot.get(Metadata_.sourceInfo).get(MetadataSourceInfo_.groupOwner),
                userGroupRoot.get(UserGroup_.id).get(UserGroupId_.groupId));
        Predicate userToGroupPredicate = cb.equal(userGroupRoot.get(UserGroup_.id).get(UserGroupId_.userId), userRoot.get(User_.id));

        Predicate basePredicate = cb.and(metadataPredicate, ownerPredicate, userToGroupPredicate);
        if (profile != null) {
            Expression<Boolean> profilePredicate = cb.equal(userGroupRoot.get(UserGroup_.profile), profile);
            query.where(cb.and(basePredicate, profilePredicate));
        } else {
            query.where(basePredicate);
        }
        if (sort != null) {
            List<Order> orders = SortUtils.sortToJpaOrders(cb, sort, userGroupRoot, metadataRoot, userRoot);
            query.orderBy(orders);
        }
        query.distinct(true);

        List<Pair<Integer, User>> results = new ArrayList<Pair<Integer, User>>();

        for (Tuple result : _entityManager.createQuery(query).getResultList()) {
            Integer mdId = (Integer) result.get(0);
            User user = (User) result.get(1);
            results.add(Pair.read(mdId, user));
        }
        return results;
    }

    @Nonnull
    @Override
    public List<User> findAllUsersThatOwnMetadata() {
        final CriteriaBuilder cb = _entityManager.getCriteriaBuilder();
        final CriteriaQuery<User> query = cb.createQuery(User.class);

        final Root<Metadata> metadataRoot = query.from(Metadata.class);
        final Root<User> userRoot = query.from(User.class);

        query.select(userRoot);

        final Path<Integer> ownerPath = metadataRoot.get(Metadata_.sourceInfo).get(MetadataSourceInfo_.owner);
        Expression<Boolean> ownerExpression = cb.equal(ownerPath, userRoot.get(User_.id));
        query.where(ownerExpression);
        query.distinct(true);

        return _entityManager.createQuery(query).getResultList();
    }

    @Nonnull
    @Override
    public List<User> findAllUsersInUserGroups(@Nonnull Specification<UserGroup> userGroupSpec) {
        final CriteriaBuilder cb = _entityManager.getCriteriaBuilder();
        final CriteriaQuery<User> query = cb.createQuery(User.class);

        final Root<UserGroup> userGroupRoot = query.from(UserGroup.class);
        final Root<User> userRoot = query.from(User.class);

        query.select(userRoot);

        final Path<Integer> ownerPath = userGroupRoot.get(UserGroup_.id).get(UserGroupId_.userId);
        Expression<Boolean> ownerExpression = cb.equal(ownerPath, userRoot.get(User_.id));
        query.where(cb.and(ownerExpression, userGroupSpec.toPredicate(userGroupRoot, query, cb)));
        query.distinct(true);

        return _entityManager.createQuery(query).getResultList();
    }

}
