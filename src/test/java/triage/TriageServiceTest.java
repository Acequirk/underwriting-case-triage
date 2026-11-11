package triage;

import org.junit.Before;
import org.junit.Test;
import triage.model.Authority;
import triage.model.CountryRisk;
import triage.model.Decision;
import triage.model.Route;
import triage.repository.InMemoryCaseRepository;
import triage.service.CaseView;
import triage.service.InvalidTransitionException;
import triage.service.NotFoundException;
import triage.service.Summary;
import triage.service.TriageService;
import triage.service.ValidationException;
import triage.testutil.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static triage.testutil.AppBuilder.app;

public class TriageServiceTest {
    private static final Instant START = Instant.parse("2026-11-02T01:00:00Z");
    private MutableClock clock;
    private TriageService service;

    @Before
    public void setUp() {
        clock = new MutableClock(START);
        service = new TriageService(new InMemoryCaseRepository(), clock);
    }

    private long submitLow() {
        return service.submit(app().build(), "intake").id();
    }

    private long submitSenior() {
        return service.submit(app().pep(true).build(), "intake").id();
    }

    // ---- submit and triage ------------------------------------------------------------

    @Test
    public void submitTriagesAndStartsTheClock() {
        CaseView v = service.submit(app().smoker(true).age(45).build(), "intake");
        assertEquals("TRIAGED", v.status());
        assertEquals("STANDARD", v.route());
        assertEquals(28, v.score());
        assertEquals("MEDIUM", v.band());
        assertEquals(START.plus(Duration.ofHours(48)).toString(), v.dueAt());
        assertEquals("ON_TRACK", v.slaStatus());
    }

    @Test
    public void submitWithMissingDocumentsHoldsTheCaseWithoutAClock() {
        CaseView v = service.submit(app().docs(false).build(), "intake");
        assertEquals("AWAITING_DOCUMENTS", v.status());
        assertEquals("AWAITING_DOCUMENTS", v.route());
        assertNull(v.dueAt());
        assertEquals("PAUSED", v.slaStatus());
    }

    @Test
    public void submitTrimsTextAndRecordsAuditTrail() {
        CaseView v = service.submit(app().ref("  APP-9  ").build(), " intake ");
        assertEquals("APP-9", v.applicantRef());
        assertEquals(2, v.audit().size());
        assertEquals("SUBMITTED", v.audit().get(0).action());
        assertEquals("intake", v.audit().get(0).actor());
        assertEquals("TRIAGED", v.audit().get(1).action());
        assertEquals("triage-engine", v.audit().get(1).actor());
        assertEquals(v.rationale(), v.audit().get(1).detail());
    }

    @Test
    public void submitRejectsInvalidValuesAndReportsAllErrors() {
        ValidationException e = assertThrows(ValidationException.class, () ->
                service.submit(app().ref(" ").age(17).sum(0).bmi(5).occupation(5).build(), "intake"));
        assertEquals(5, e.errors().size());
        assertTrue(e.errors().contains("applicantRef is required"));
        assertTrue(e.errors().contains("age must be between 18 and 75"));
        assertTrue(e.errors().contains("sumAssured must be greater than 0"));
        assertTrue(e.errors().contains("bmi must be between 10 and 60"));
        assertTrue(e.errors().contains("occupationClass must be between 1 and 4"));
    }

    @Test
    public void submitRequiresAnActorAndCapsSumAssured() {
        assertThrows(ValidationException.class, () -> service.submit(app().build(), " "));
        assertThrows(ValidationException.class, () -> service.submit(app().sum(10_000_001).build(), "intake"));
    }

    @Test
    public void idsAreSequentialAndUnknownIdsAreNotFound() {
        assertEquals(1, submitLow());
        assertEquals(2, submitLow());
        assertThrows(NotFoundException.class, () -> service.get(99));
    }

    // ---- documents --------------------------------------------------------------------

    @Test
    public void receivingDocumentsRetriagesAndStartsTheClockFromThatMoment() {
        long id = service.submit(app().docs(false).smoker(true).build(), "intake").id();
        clock.advance(Duration.ofHours(10));
        CaseView v = service.receiveDocuments(id, "ops");
        assertEquals("TRIAGED", v.status());
        assertEquals("FAST_TRACK", v.route());   // score 20
        assertEquals(START.plus(Duration.ofHours(10 + 24)).toString(), v.dueAt());
        assertEquals("DOCUMENTS_RECEIVED", v.audit().get(2).action());
        assertEquals("TRIAGED", v.audit().get(3).action());
    }

    @Test
    public void documentsCanOnlyBeReceivedOnce() {
        long id = submitLow();
        assertThrows(InvalidTransitionException.class, () -> service.receiveDocuments(id, "ops"));
    }

    // ---- assignment and authority -----------------------------------------------------

    @Test
    public void assignMovesToAssigned() {
        long id = submitLow();
        CaseView v = service.assign(id, "Alex", Authority.JUNIOR);
        assertEquals("ASSIGNED", v.status());
        assertEquals("Alex", v.assignee());
        assertEquals("JUNIOR", v.assigneeAuthority());
    }

    @Test
    public void seniorReviewNeedsSeniorAuthority() {
        long id = submitSenior();
        InvalidTransitionException e = assertThrows(InvalidTransitionException.class,
                () -> service.assign(id, "Alex", Authority.JUNIOR));
        assertTrue(e.getMessage().contains("senior underwriter"));
        assertEquals("ASSIGNED", service.assign(id, "Dana", Authority.SENIOR).status());
    }

    @Test
    public void cannotAssignWhileAwaitingDocumentsOrAfterDecision() {
        long waiting = service.submit(app().docs(false).build(), "intake").id();
        assertThrows(InvalidTransitionException.class, () -> service.assign(waiting, "Alex", Authority.JUNIOR));
        long done = submitLow();
        service.assign(done, "Alex", Authority.JUNIOR);
        service.decide(done, "Alex", Decision.APPROVE, null);
        assertThrows(InvalidTransitionException.class, () -> service.assign(done, "Sam", Authority.JUNIOR));
    }

    @Test
    public void assignValidatesInput() {
        long id = submitLow();
        assertThrows(ValidationException.class, () -> service.assign(id, " ", Authority.JUNIOR));
        assertThrows(ValidationException.class, () -> service.assign(id, "Alex", null));
    }

    @Test
    public void reassigningAnAssignedCaseIsAllowed() {
        long id = submitLow();
        service.assign(id, "Alex", Authority.JUNIOR);
        assertEquals("Sam", service.assign(id, "Sam", Authority.JUNIOR).assignee());
    }

    // ---- decisions --------------------------------------------------------------------

    @Test
    public void onlyTheAssigneeCanDecide() {
        long id = submitLow();
        service.assign(id, "Alex", Authority.JUNIOR);
        InvalidTransitionException e = assertThrows(InvalidTransitionException.class,
                () -> service.decide(id, "Sam", Decision.APPROVE, null));
        assertTrue(e.getMessage().contains("Alex"));
    }

    @Test
    public void decisionNeedsAnAssignedCase() {
        long id = submitLow();
        assertThrows(InvalidTransitionException.class, () -> service.decide(id, "Alex", Decision.APPROVE, null));
    }

    @Test
    public void declineAndCounterOfferNeedAReasonButApproveDoesNot() {
        long a = submitLow();
        service.assign(a, "Alex", Authority.JUNIOR);
        assertThrows(ValidationException.class, () -> service.decide(a, "Alex", Decision.DECLINE, "short"));
        assertThrows(ValidationException.class, () -> service.decide(a, "Alex", Decision.COUNTER_OFFER, null));
        assertEquals("DECIDED", service.decide(a, "Alex", Decision.APPROVE, null).status());

        long b = submitLow();
        service.assign(b, "Alex", Authority.JUNIOR);
        CaseView declined = service.decide(b, "Alex", Decision.DECLINE, "  Undisclosed medical history  ");
        assertEquals("DECLINE", declined.decision());
        assertEquals("Undisclosed medical history", declined.decisionReason());
    }

    @Test
    public void aDecidedCaseCannotBeDecidedAgain() {
        long id = submitLow();
        service.assign(id, "Alex", Authority.JUNIOR);
        service.decide(id, "Alex", Decision.APPROVE, null);
        assertThrows(InvalidTransitionException.class, () -> service.decide(id, "Alex", Decision.DECLINE, "changed my mind about it"));
    }

    // ---- overrides --------------------------------------------------------------------

    @Test
    public void overrideChangesRouteAndDueTimeAndIsAudited() {
        long id = service.submit(app().smoker(true).age(45).build(), "intake").id();   // STANDARD, 48h
        CaseView v = service.overrideRoute(id, "Dana", Route.FAST_TRACK, "Clean medical report received");
        assertEquals("FAST_TRACK", v.route());
        assertEquals("STANDARD", v.triageRoute());
        assertTrue(v.overridden());
        assertEquals(START.plus(Duration.ofHours(24)).toString(), v.dueAt());
        assertEquals("OVERRIDE", v.audit().get(v.audit().size() - 1).action());
        assertTrue(v.audit().get(v.audit().size() - 1).detail().contains("STANDARD -> FAST_TRACK"));
        assertTrue(v.audit().get(v.audit().size() - 1).detail().contains("Clean medical report received"));
    }

    @Test
    public void overridingBackToTheTriageRouteClearsTheOverrideFlag() {
        long id = service.submit(app().smoker(true).age(45).build(), "intake").id();
        service.overrideRoute(id, "Dana", Route.FAST_TRACK, "Clean medical report received");
        assertFalse(service.overrideRoute(id, "Dana", Route.STANDARD, "Report was for another applicant").overridden());
    }

    @Test
    public void overrideNeedsAReasonAndAReviewRoute() {
        long id = submitLow();
        assertThrows(ValidationException.class, () -> service.overrideRoute(id, "Dana", Route.STANDARD, "ok"));
        assertThrows(ValidationException.class, () -> service.overrideRoute(id, "Dana", Route.AWAITING_DOCUMENTS, "valid reason here"));
        assertThrows(ValidationException.class, () -> service.overrideRoute(id, "Dana", null, "valid reason here"));
        assertThrows(ValidationException.class, () -> service.overrideRoute(id, "Dana", Route.FAST_TRACK, "valid reason here"));   // already on it
    }

    @Test
    public void hardRuleCasesCannotBeDowngraded() {
        long id = submitSenior();
        InvalidTransitionException e = assertThrows(InvalidTransitionException.class,
                () -> service.overrideRoute(id, "Dana", Route.STANDARD, "Looks fine to me really"));
        assertTrue(e.getMessage().contains("R10"));
        assertEquals("SENIOR_REVIEW", service.get(id).route());
    }

    @Test
    public void hardRuleNamesAreListedForEveryHardRuleThatFired() {
        long id = service.submit(app().pep(true).country(CountryRisk.HIGH).build(), "intake").id();
        InvalidTransitionException e = assertThrows(InvalidTransitionException.class,
                () -> service.overrideRoute(id, "Dana", Route.STANDARD, "Looks fine to me really"));
        assertTrue(e.getMessage().contains("R09, R10"));
    }

    @Test
    public void highScoreWithoutHardRuleCanBeDowngradedByAHuman() {
        long id = service.submit(app().smoker(true).age(55).bmi(35).build(), "intake").id();   // 50, no hard rule
        assertEquals("SENIOR_REVIEW", service.get(id).route());
        assertEquals("STANDARD", service.overrideRoute(id, "Dana", Route.STANDARD, "Medical report explains BMI").route());
    }

    @Test
    public void escalatingAnAssignedJuniorCaseToSeniorReviewUnassignsIt() {
        long id = service.submit(app().smoker(true).age(45).build(), "intake").id();
        service.assign(id, "Alex", Authority.JUNIOR);
        CaseView v = service.overrideRoute(id, "Dana", Route.SENIOR_REVIEW, "Concerns about disclosure");
        assertEquals("TRIAGED", v.status());
        assertNull(v.assignee());
        assertEquals("UNASSIGNED", v.audit().get(v.audit().size() - 1).action());
    }

    @Test
    public void escalatingACaseHeldBySeniorKeepsTheAssignee() {
        long id = service.submit(app().smoker(true).age(45).build(), "intake").id();
        service.assign(id, "Dana", Authority.SENIOR);
        CaseView v = service.overrideRoute(id, "Dana", Route.SENIOR_REVIEW, "Concerns about disclosure");
        assertEquals("ASSIGNED", v.status());
        assertEquals("Dana", v.assignee());
    }

    @Test
    public void cannotOverrideWhenWaitingForDocumentsOrAfterDecision() {
        long waiting = service.submit(app().docs(false).build(), "intake").id();
        assertThrows(InvalidTransitionException.class, () -> service.overrideRoute(waiting, "Dana", Route.STANDARD, "valid reason here"));
        long done = submitLow();
        service.assign(done, "Alex", Authority.JUNIOR);
        service.decide(done, "Alex", Decision.APPROVE, null);
        assertThrows(InvalidTransitionException.class, () -> service.overrideRoute(done, "Dana", Route.STANDARD, "valid reason here"));
    }

    // ---- SLA --------------------------------------------------------------------------

    @Test
    public void slaMovesFromOnTrackToAtRiskToBreached() {
        long id = submitLow();                                   // FAST_TRACK, 24h window
        assertEquals("ON_TRACK", service.get(id).slaStatus());
        clock.advance(Duration.ofHours(17).plusMinutes(59));     // 6h01 left: still on track
        assertEquals("ON_TRACK", service.get(id).slaStatus());
        clock.advance(Duration.ofMinutes(1));                    // exactly 6h left = 25%
        assertEquals("AT_RISK", service.get(id).slaStatus());
        clock.advance(Duration.ofHours(6));                      // exactly due: not yet breached
        assertEquals("AT_RISK", service.get(id).slaStatus());
        clock.advance(Duration.ofMinutes(1));
        CaseView v = service.get(id);
        assertEquals("BREACHED", v.slaStatus());
        assertEquals(Long.valueOf(-1), v.minutesRemaining());
    }

    @Test
    public void decidingInTimeMeetsTheSlaAndDecidingLateBreachesIt() {
        long onTime = submitLow();
        service.assign(onTime, "Alex", Authority.JUNIOR);
        clock.advance(Duration.ofHours(23));
        assertEquals("MET", service.decide(onTime, "Alex", Decision.APPROVE, null).slaStatus());

        long late = submitLow();
        service.assign(late, "Alex", Authority.JUNIOR);
        clock.advance(Duration.ofHours(25));
        assertEquals("BREACHED", service.decide(late, "Alex", Decision.APPROVE, null).slaStatus());
        clock.advance(Duration.ofDays(30));
        assertEquals("BREACHED", service.get(late).slaStatus());   // outcome is fixed once decided
        assertEquals("MET", service.get(onTime).slaStatus());
    }

    @Test
    public void pausedCasesDoNotBreachWhileWaitingForDocuments() {
        long id = service.submit(app().docs(false).build(), "intake").id();
        clock.advance(Duration.ofDays(10));
        assertEquals("PAUSED", service.get(id).slaStatus());
    }

    // ---- queue, list, summary ---------------------------------------------------------

    @Test
    public void queueIsOrderedByDueTimeThenScoreThenId() {
        long standard = service.submit(app().smoker(true).age(45).build(), "intake").id();   // due +48h, score 28
        clock.advance(Duration.ofHours(1));
        long fastLater = submitLow();                                                         // due +25h
        long seniorNow = submitSenior();                                                      // due +73h
        long fastSameTimeHigherScore = service.submit(app().smoker(true).build(), "intake").id();   // due +25h, score 20
        List<Long> order = service.queue().stream().map(CaseView::id).toList();
        assertEquals(List.of(fastSameTimeHigherScore, fastLater, standard, seniorNow), order);
    }

    @Test
    public void queueExcludesDecidedAndWaitingCases() {
        long open = submitLow();
        long waiting = service.submit(app().docs(false).build(), "intake").id();
        long done = submitLow();
        service.assign(done, "Alex", Authority.JUNIOR);
        service.decide(done, "Alex", Decision.APPROVE, null);
        List<Long> ids = service.queue().stream().map(CaseView::id).toList();
        assertEquals(List.of(open), ids);
        assertFalse(ids.contains(waiting));
    }

    @Test
    public void listFiltersByStatusRouteAndBand() {
        submitLow();                                  // 1 FAST_TRACK LOW
        submitSenior();                               // 2 SENIOR_REVIEW MEDIUM
        service.submit(app().docs(false).build(), "intake");   // 3 AWAITING
        assertEquals(1, service.list("TRIAGED", "FAST_TRACK", null).size());
        assertEquals(1, service.list(null, "senior_review", null).size());
        assertEquals(1, service.list("awaiting_documents", null, null).size());
        assertEquals(1, service.list(null, null, "MEDIUM").size());
        assertEquals(3, service.list(null, null, null).size());
        assertEquals(3, service.list("", "", "").size());
    }

    @Test
    public void listRejectsUnknownFilterValues() {
        assertThrows(ValidationException.class, () -> service.list("nope", null, null));
        assertThrows(ValidationException.class, () -> service.list(null, "nope", null));
        assertThrows(ValidationException.class, () -> service.list(null, null, "nope"));
    }

    @Test
    public void listViewsOmitTheAuditTrailButGetIncludesIt() {
        long id = submitLow();
        assertTrue(service.list(null, null, null).get(0).audit().isEmpty());
        assertFalse(service.get(id).audit().isEmpty());
    }

    @Test
    public void summaryCountsEverything() {
        submitLow();                                              // 1 LOW fast-track
        submitSenior();                                           // 2 MEDIUM senior
        service.submit(app().docs(false).build(), "intake");      // 3 awaiting
        service.assign(1, "Alex", Authority.JUNIOR);
        service.decide(1, "Alex", Decision.APPROVE, null);
        clock.advance(Duration.ofHours(70));                      // case 2 (72h) is at risk
        Summary s = service.summary();
        assertEquals(3, s.total());
        assertEquals(1, s.awaitingDocuments());
        assertEquals(1, s.open());
        assertEquals(1, s.decided());
        assertEquals(1, s.atRisk());
        assertEquals(0, s.breached());
        assertEquals(Integer.valueOf(2), s.byBand().get("LOW"));
        assertEquals(Integer.valueOf(1), s.byBand().get("MEDIUM"));
        assertEquals(Integer.valueOf(1), s.byRoute().get("SENIOR_REVIEW"));
        assertEquals(Integer.valueOf(1), s.decisions().get("APPROVE"));
        assertEquals(Integer.valueOf(0), s.decisions().get("DECLINE"));
        clock.advance(Duration.ofHours(3));
        assertEquals(1, service.summary().breached());
    }

    @Test
    public void auditTrailIsCompleteOrderedAndReadOnly() {
        long id = submitLow();
        service.assign(id, "Alex", Authority.JUNIOR);
        service.decide(id, "Alex", Decision.APPROVE, null);
        List<String> actions = service.get(id).audit().stream().map(CaseView.AuditView::action).toList();
        assertEquals(List.of("SUBMITTED", "TRIAGED", "ASSIGNED", "DECISION"), actions);
        assertNotNull(service.get(id).audit().get(0).at());
    }

    @Test
    public void everyStateChangeIsAudited() {
        long id = service.submit(app().docs(false).smoker(true).age(45).build(), "intake").id();
        service.receiveDocuments(id, "ops");
        service.overrideRoute(id, "Dana", Route.FAST_TRACK, "Clean medical report received");
        service.assign(id, "Alex", Authority.JUNIOR);
        service.decide(id, "Alex", Decision.COUNTER_OFFER, "Offer with a premium loading");
        List<String> actions = service.get(id).audit().stream().map(CaseView.AuditView::action).toList();
        assertEquals(List.of("SUBMITTED", "TRIAGED", "DOCUMENTS_RECEIVED", "TRIAGED", "OVERRIDE", "ASSIGNED", "DECISION"), actions);
    }
}
