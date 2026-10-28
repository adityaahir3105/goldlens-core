package com.goldlens.controller;

import com.goldlens.scheduler.DxyScheduler;
import com.goldlens.scheduler.GoldPriceScheduler;
import com.goldlens.scheduler.GoldRiskScheduler;
import com.goldlens.scheduler.RealYieldScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class JobControllerTest {

    private final GoldPriceScheduler goldPrice = mock(GoldPriceScheduler.class);
    private final RealYieldScheduler realYield = mock(RealYieldScheduler.class);
    private final DxyScheduler dxy = mock(DxyScheduler.class);
    private final GoldRiskScheduler goldRisk = mock(GoldRiskScheduler.class);
    private final JobController controller = new JobController(goldPrice, realYield, dxy, goldRisk);

    @Test
    void goldPriceRunsOnlyThePriceJob() {
        assertEquals(200, controller.goldPrice().getStatusCode().value());
        verify(goldPrice).persistGoldPriceSnapshot();
        verifyNoInteractions(realYield, dxy, goldRisk);
    }

    @Test
    void dailyRunsIndicatorsBeforeTheRiskRollUp() {
        assertEquals(200, controller.daily().getStatusCode().value());
        InOrder order = inOrder(realYield, dxy, goldRisk);
        order.verify(realYield).ingestRealYield();
        order.verify(dxy).ingestDxy();
        order.verify(goldRisk).computeDailyGoldRisk();
        verifyNoInteractions(goldPrice);
    }

    @Test
    void dailyStopsAndFailsWhenAJobThrows() {
        doThrow(new IllegalStateException("FRED down")).when(dxy).ingestDxy();
        assertThrows(IllegalStateException.class, controller::daily);
        verify(goldRisk, never()).computeDailyGoldRisk();
    }
}
