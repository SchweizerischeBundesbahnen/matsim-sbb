//package ch.ethz.matsim.lima_poc3;
//
//import com.google.inject.Provider;
//import org.matsim.api.core.v01.population.Plan;
//import org.matsim.api.core.v01.replanning.PlanStrategyModule;
//import org.matsim.core.config.groups.GlobalConfigGroup;
//import org.matsim.core.gbl.MatsimRandom;
//import org.matsim.core.population.algorithms.PlanAlgorithm;
//import org.matsim.core.replanning.ReplanningContext;
//import org.matsim.core.router.TripRouter;
//import org.matsim.facilities.ActivityFacilities;
//
//import java.util.LinkedList;
//import java.util.List;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.concurrent.atomic.AtomicReference;
//import java.util.logging.Logger;
//
//public class PlanParallelizer implements PlanStrategyModule {
//    private final int numOfThreads;
//
//    private final ActivityFacilities facilities;
//    private final Provider<TripRouter> tripRouterProvider;
//    private final Provider<IntermodalRouterModel> intermodalRouterModelProvider;
//    private PlanAlgoThread[] algothreads = null;
//    private Thread[] threads = null;
//    private PlanAlgorithm directAlgo = null;
//    private String name = null;
//    private List<Plan> plans;
//    private boolean writeToPlans = true;
//
//    private int count = 0;
//
//    private final AtomicReference<Throwable> hadException = new AtomicReference<>(null);
//    private final ExceptionHandler exceptionHandler = new ExceptionHandler(this.hadException);
//
//    private ReplanningContext replanningContext;
//
//    static final private Logger log = Logger.getLogger(IntermodalRouterPlanParallelizer.class);
//
//    public IntermodalRouterPlanParallelizer(ActivityFacilities facilities, Provider<TripRouter> tripRouterProvider, GlobalConfigGroup globalConfigGroup, Provider<IntermodalRouterModel> intermodalRouterModelProvider) {
//        this.numOfThreads = globalConfigGroup.getNumberOfThreads();
//        this.facilities = facilities;
//        this.tripRouterProvider = tripRouterProvider;
//        this.intermodalRouterModelProvider = intermodalRouterModelProvider;
//    }
//
//    public PlanAlgorithm getPlanAlgoInstance() {
//        return new IntermodalPlanRouter(MatsimRandom.getLocalInstance(), tripRouterProvider.get(), facilities, intermodalRouterModelProvider.get());
//    }
//
//    protected void beforePrepareReplanningHook(@SuppressWarnings("unused") ReplanningContext replanningContextTmp) {
//        // left empty for inheritance
//    }
//
//    protected void afterPrepareReplanningHook(@SuppressWarnings("unused") ReplanningContext replanningContextTmp) {
//        // left empty for inheritance
//    }
//
//    public final void prepareReplanning(ReplanningContext replanningContextTmp, boolean writeToPlans) {
//        this.writeToPlans = writeToPlans;
//        prepareReplanning(replanningContextTmp);
//    }
//
//    @Override
//    public final void prepareReplanning(ReplanningContext replanningContextTmp) {
//        plans = new LinkedList<>();
//        this.beforePrepareReplanningHook(replanningContextTmp);
//        this.replanningContext = replanningContextTmp;
//        if (this.numOfThreads == 0) {
//            // it seems, no threads are desired :(
//            this.directAlgo = getPlanAlgoInstance();
//        } else {
//            initThreads();
//        }
//        this.afterPrepareReplanningHook(replanningContextTmp);
//    }
//
//    protected final ReplanningContext getReplanningContext() {
//        return replanningContext;
//    }
//
//    @Override
//    public final void handlePlan(final Plan plan) {
//        plans.add(plan);
//        if (this.directAlgo == null) {
////			this.algothreads[this.count % this.numOfThreads].addPlanToThread(plan);
//            this.count++;
//        } else {
//            if (directAlgo instanceof IntermodalPlanRouter) {
//                ((IntermodalPlanRouter) directAlgo).run(plan, writeToPlans);
//            } else {
//                this.directAlgo.run(plan);
//            }
//        }
//    }
//
//    protected void beforeFinishReplanningHook() {
//        // left empty for inheritance
//    }
//
//    protected void afterFinishReplanningHook() {
//        // left empty for inheritance
//    }
//
//
//    @Override
//    public final void finishReplanning() {
//        this.beforeFinishReplanningHook();
//        if (this.directAlgo == null) {
//            // only try to start threads if we did not directly work on all the plans
//            log.info("[" + this.name + "] starting " + this.threads.length + " threads, handling " + this.count + " plans");
//
//            // start threads
//            for (Thread thread : this.threads) {
//                thread.start();
//            }
//
//            // wait until each thread is finished
//            try {
//                for (Thread thread : this.threads) {
//                    thread.join();
//                }
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//            log.info("[" + this.name + "] all " + this.threads.length + " threads finished.");
//            Throwable throwable = this.hadException.get();
//            if (throwable != null) {
//                throw new RuntimeException("Some threads crashed, thus not all plans may have been handled.", throwable);
//            }
//        }
//        // reset
//        this.algothreads = null;
//        this.threads = null;
//        this.replanningContext = null;
//        this.count = 0;
//
//        writeToPlans = true;
//        this.afterFinishReplanningHook();
//    }
//
//    private void initThreads() {
//        if (this.threads != null) {
//            throw new RuntimeException("threads are already initialized");
//        }
//
//        this.hadException.set(null);
//        this.threads = new Thread[this.numOfThreads];
//        this.algothreads = new PlanAlgoThread[this.numOfThreads];
//
//        AtomicInteger counter = new AtomicInteger();
//        AtomicInteger finishedCounter = new AtomicInteger();
//        // setup threads
//        for (int i = 0; i < this.numOfThreads; i++) {
//            PlanAlgorithm algo = getPlanAlgoInstance();
//            if (i == 0) {
//                this.name = algo.getClass().getSimpleName();
////				counter = new Counter("[" + this.name + "] handled plan # ");
//            }
//            PlanAlgoThread algothread = new PlanAlgoThread(algo, counter, finishedCounter, plans, writeToPlans);
//            Thread thread = new Thread(algothread, this.name + "." + i);
//            thread.setUncaughtExceptionHandler(this.exceptionHandler);
//            this.threads[i] = thread;
//            this.algothreads[i] = algothread;
//        }
//    }
//
//    /* package (for a test) */
//    final int getNumOfThreads() {
//        return numOfThreads;
//    }
//
//    private final static class ExceptionHandler implements Thread.UncaughtExceptionHandler {
//
//        private final AtomicReference<Throwable> hadException;
//
//        public ExceptionHandler(final AtomicReference<Throwable> hadException) {
//            this.hadException = hadException;
//        }
//
//        @Override
//        public void uncaughtException(Thread t, Throwable e) {
//            log.error("Thread " + t.getName() + " died with exception. Will stop after all threads finished.", e);
//            this.hadException.set(e);
//        }
//
//    }
//
//    private final static class PlanAlgoThread implements Runnable {
//
//        private final PlanAlgorithm planAlgo;
//        private final List<Plan> plans;
//        private final AtomicInteger counter;
//        private final AtomicInteger finishedCounter;
//        private final boolean writeToPlans;
//
//        public PlanAlgoThread(final PlanAlgorithm algo, final AtomicInteger counter, final AtomicInteger finishedCounter, final List<Plan> plans, boolean writeToPlans) {
//            this.planAlgo = algo;
//            this.counter = counter;
//            this.finishedCounter = finishedCounter;
//            this.plans = plans;
//            this.writeToPlans = writeToPlans;
//        }
//
//        @Override
//        public void run() {
//            while (true) {
//                int planNr = this.counter.incrementAndGet() - 1;
//                if (planNr < plans.size()) {
//                    // run
//                    if (this.planAlgo instanceof IntermodalPlanRouter) {
//                        ((IntermodalPlanRouter) this.planAlgo).run(plans.get(planNr), writeToPlans);
//                    } else {
//                        this.planAlgo.run(plans.get(planNr));
//                    }
//
//                    // log if power of 2
//                    int finishedCounter = this.finishedCounter.incrementAndGet();
//                    boolean powerOf2 = (finishedCounter & (finishedCounter - 1)) == 0; // 10000 & 01111 == 00000
//                    if (powerOf2) {
//                        log.info(String.format("[%s] handled plan # %d", planAlgo.getClass().getSimpleName(), finishedCounter));
//                    }
//                } else {
//                    break;
//                }
//            }
//        }
//    }
//}
