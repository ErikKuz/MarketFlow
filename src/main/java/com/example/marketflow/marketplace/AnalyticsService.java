package com.example.marketflow.marketplace;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.HttpStatus;
import static com.example.marketflow.marketplace.MarketplaceViews.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class AnalyticsService {
    private final NamedParameterJdbcTemplate jdbc;
    private final MarketplaceAccess access;

    @Transactional(readOnly = true)
    public Report report(Long actor, Instant from, Instant to) {
        access.require(actor, "ANALYST", "OWNER");
        if (from == null || to == null || !from.isBefore(to))
            throw new MarketplaceException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "from must precede to (exclusive)");
        var params = Map.of("from", Timestamp.from(from), "to", Timestamp.from(to));
        long created = jdbc.queryForObject("select count(*) from orders where created_at >= :from and created_at < :to", params, Long.class);
        long cancelled = jdbc.queryForObject("select count(distinct order_id) from audit_events where action = 'ORDER_CANCELLED' and created_at >= :from and created_at < :to", params, Long.class);
        var totals = jdbc.queryForMap("""
                select
                count(distinct case when type = 'PAYMENT' then order_id end) as paid,
                count(distinct case when type = 'REFUND' then order_id end) as refunded_orders,
                coalesce(sum(case when type = 'PAYMENT' then amount else 0 end), 0) as gross,
                coalesce(sum(case when type = 'REFUND' then amount else 0 end), 0) as refunds,
                coalesce(sum(case when type = 'PLATFORM_COMMISSION' then amount else 0 end), 0) as commission,
                coalesce(sum(case when type = 'PLATFORM_COMMISSION_REVERSAL' then amount else 0 end), 0) as reversed
                from payment_transactions where status in ('COMPLETED', 'REFUNDED')
                and created_at >= :from and created_at < :to
                """, params);
        var popular = jdbc.query("""
                select oi.product_id, max(oi.product_name) as product_name,
                sum(oi.quantity) as quantity, sum(oi.total_price) as gross
                from order_items oi join payment_transactions p on p.order_id = oi.order_id
                where p.type = 'PAYMENT' and p.status in ('COMPLETED', 'REFUNDED')
                and p.created_at >= :from and p.created_at < :to
                group by oi.product_id order by quantity desc, oi.product_id limit 20
                """, params, (rs, row) -> new PopularProduct(rs.getLong("product_id"), rs.getString("product_name"),
                        rs.getLong("quantity"), rs.getBigDecimal("gross")));
        var commission = money(totals.get("commission"));
        var reversed = money(totals.get("reversed"));
        return new Report(from, to, created, ((Number) totals.get("paid")).longValue(), cancelled,
                ((Number) totals.get("refunded_orders")).longValue(), money(totals.get("gross")),
                money(totals.get("refunds")), commission, reversed, commission.subtract(reversed), popular);
    }
    private java.math.BigDecimal money(Object value) { return new java.math.BigDecimal(value.toString()); }
}
