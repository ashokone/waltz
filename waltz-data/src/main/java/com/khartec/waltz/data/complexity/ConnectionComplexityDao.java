package com.khartec.waltz.data.complexity;

import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.tally.ImmutableTally;
import com.khartec.waltz.model.tally.Tally;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.schema.tables.LogicalFlow.LOGICAL_FLOW;

@Repository
public class ConnectionComplexityDao {

    private static final Field<Integer> CONNECTION_COUNT_ALIAS = DSL.field("connection_count", Integer.class);
    private static final Field<Long> APP_ID_ALIAS = DSL.field("app_id", Long.class);
    private static final Field<Integer> TOTAL_CONNECTIONS_FIELD = DSL.field("total_connections", Integer.class);
    private static final Field<Long> SOURCE_APP_FIELD = LOGICAL_FLOW.SOURCE_ENTITY_ID.as(APP_ID_ALIAS);
    private static final Field<Long> TARGET_APP_FIELD = LOGICAL_FLOW.TARGET_ENTITY_ID.as(APP_ID_ALIAS);
    private static final Field<Integer> TARGET_COUNT_FIELD = DSL.countDistinct(LOGICAL_FLOW.TARGET_ENTITY_ID).as(CONNECTION_COUNT_ALIAS);
    private static final Field<Integer> SOURCE_COUNT_FIELD = DSL.countDistinct(LOGICAL_FLOW.SOURCE_ENTITY_ID).as(CONNECTION_COUNT_ALIAS);


    private static final String APPLICATION_KIND = EntityKind.APPLICATION.name();

    private static final Condition BOTH_ARE_APPLICATIONS =
            LOGICAL_FLOW.SOURCE_ENTITY_KIND
                    .eq(APPLICATION_KIND)
                    .and(LOGICAL_FLOW.TARGET_ENTITY_KIND
                            .eq(APPLICATION_KIND));

    private static final SelectHavingStep<Record2<Long, Integer>> OUTBOUND_FLOWS =
            DSL.select(SOURCE_APP_FIELD, TARGET_COUNT_FIELD)
                    .from(LOGICAL_FLOW)
                    .where(BOTH_ARE_APPLICATIONS)
                    .groupBy(LOGICAL_FLOW.SOURCE_ENTITY_ID);

    private static final SelectHavingStep<Record2<Long, Integer>> INBOUND_FLOWS =
            DSL.select(TARGET_APP_FIELD, SOURCE_COUNT_FIELD)
                    .from(LOGICAL_FLOW)
                    .where(BOTH_ARE_APPLICATIONS)
                    .groupBy(LOGICAL_FLOW.TARGET_ENTITY_ID);

    private static final SelectHavingStep<Record2<Long, BigDecimal>> TOTAL_FLOW_COUNTS =
            DSL.select(APP_ID_ALIAS, DSL.sum(CONNECTION_COUNT_ALIAS).as(TOTAL_CONNECTIONS_FIELD))
                    .from(OUTBOUND_FLOWS.unionAll(INBOUND_FLOWS))
                    .groupBy(APP_ID_ALIAS);

    private DSLContext dsl;


    @Autowired
    public ConnectionComplexityDao(DSLContext dsl) {
        this.dsl = dsl;
        checkNotNull(dsl, "DSL cannot be null");
    }


    // ---- convenience functions

    public int findBaseline() {
        return findBaseline(DSL.trueCondition());
    }

    public int findBaseline(Select<Record1<Long>> appIdProvider) {
        return findBaseline(APP_ID_ALIAS.in(appIdProvider));
    }

    public int findBaseline(Long appIds) {
        return findBaseline(APP_ID_ALIAS.in(appIds));
    }

    public List<Tally<Long>> findCounts() {
        return findCounts(DSL.trueCondition());
    }

    public List<Tally<Long>> findCounts(Select<Record1<Long>> appIdProvider) {
        return findCounts(APP_ID_ALIAS.in(appIdProvider));
    }

    public List<Tally<Long>> findCounts(Long... appIds) {
        return findCounts(APP_ID_ALIAS.in(appIds));
    }


    // ---- base queries

    private int findBaseline(Condition condition) {

        return dsl.select(DSL.max(TOTAL_CONNECTIONS_FIELD))
                .from(TOTAL_FLOW_COUNTS)
                .where(condition)
                .fetchOne(r -> r.value1());
    }


    private List<Tally<Long>> findCounts(Condition condition) {
        return dsl.select(APP_ID_ALIAS, TOTAL_CONNECTIONS_FIELD)
                .from(TOTAL_FLOW_COUNTS)
                .where(condition)
                .fetch(r -> ImmutableTally.<Long>builder()
                        .id(r.value1())
                        .count(r.value2())
                        .build());
    }

}
