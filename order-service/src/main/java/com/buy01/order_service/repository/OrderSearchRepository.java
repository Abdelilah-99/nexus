package com.buy01.order_service.repository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import com.buy01.order_service.dtos.req.OrderFilter;
import com.buy01.order_service.models.Order;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderSearchRepository {
    private static final String FULFILLMENTS = "fulfillments";
    private static final String STATUS = "status";
    private static final String ARCHIVED_BY = "archivedBy";

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final MongoTemplate mongoTemplate;

    public Page<Order> findCustomerOrders(String customerId, OrderFilter filter, Pageable pageable) {
        return find(Criteria.where("customerId").is(customerId), filter, pageable, customerId);
    }

    public Page<Order> findSellerOrders(String sellerId, OrderFilter filter, Pageable pageable) {
        Criteria owner = Criteria.where("sellerIds").is(sellerId);
        List<Criteria> constraints = new ArrayList<>();
        constraints.add(owner);
        if (filter.status() != null) {
            constraints.add(new Criteria().orOperator(
                    Criteria.where(FULFILLMENTS).elemMatch(Criteria.where("sellerId").is(sellerId)
                            .and(STATUS).is(filter.status())),
                    new Criteria().andOperator(new Criteria().orOperator(
                            Criteria.where(FULFILLMENTS).exists(false), Criteria.where(FULFILLMENTS).is(null),
                            Criteria.where(FULFILLMENTS).size(0)), Criteria.where(STATUS).is(filter.status()))));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            String text = filter.q().trim();
            constraints.add(new Criteria().orOperator(Criteria.where("id").is(text),
                    Criteria.where("items").elemMatch(Criteria.where("sellerId").is(sellerId)
                            .and("productName").regex(Pattern.quote(text), "i"))));
        }
        return find(constraints.size() == 1 ? owner : new Criteria().andOperator(constraints),
                new OrderFilter(null, filter.from(), filter.to(), null, filter.archived()), pageable, sellerId);
    }

    private Criteria archiveVisibility(String userId, OrderFilter filter) {
        return Boolean.TRUE.equals(filter.archived())
                ? Criteria.where(ARCHIVED_BY).is(userId) : Criteria.where(ARCHIVED_BY).ne(userId);
    }

    private Page<Order> find(Criteria owner, OrderFilter filter, Pageable pageable, String viewerId) {
        Query query = buildQuery(owner, filter);
        query.addCriteria(archiveVisibility(viewerId, filter));
        List<Order> orders = mongoTemplate.find(Query.of(query).with(NEWEST_FIRST).with(pageable), Order.class);
        // The count is skipped when the page alone tells the total (e.g. a first page that is not full)
        return PageableExecutionUtils.getPage(orders, pageable, () -> mongoTemplate.count(query, Order.class));
    }

    Query buildQuery(Criteria owner, OrderFilter filter) {
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(owner);

        if (filter.status() != null) {
            criteria.add(Criteria.where(STATUS).is(filter.status()));
        }
        if (filter.from() != null || filter.to() != null) {
            Criteria createdAt = Criteria.where("createdAt");
            if (filter.from() != null) {
                createdAt.gte(filter.from().atStartOfDay(ZoneOffset.UTC).toInstant());
            }
            if (filter.to() != null) {
                // "to" is inclusive: everything before the start of the next day
                createdAt.lt(filter.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
            }
            criteria.add(createdAt);
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            String text = filter.q().trim();
            // Quoted so user input is matched literally, never interpreted as a regex
            criteria.add(new Criteria().orOperator(
                    Criteria.where("id").is(text),
                    Criteria.where("items.productName").regex(Pattern.quote(text), "i")));
        }

        return new Query(new Criteria().andOperator(criteria));
    }
}
