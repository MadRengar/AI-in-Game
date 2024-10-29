package players.GroupD;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import players.IAnyTimePlayer;
import players.PlayerConstants;
import players.mcts.MASTPlayer;
import players.simple.RandomPlayer;
import utilities.ElapsedCpuTimer;
import utilities.Pair;
import utilities.Utils;

import java.util.*;
import java.util.stream.Collectors;

public class RHEAPlayer extends AbstractPlayer implements IAnyTimePlayer {
    private static final AbstractPlayer randomPlayer = new RandomPlayer();
    List<Map<Object, Pair<Integer, Double>>> MASTStatistics; // a list of one Map per player. Action -> (visits, totValue)
    protected List<RHEAIndividual> population = new ArrayList<>();
    // Budgets
    protected double timePerIteration = 0, timeTaken = 0, initTime = 0;
    protected int numIters = 0;
    protected int fmCalls = 0;
    protected int copyCalls = 0;
    protected int repairCount, nonRepairCount;
    private MASTPlayer mastPlayer;

    private double prevBestValue;
    private double currentBestValue;
    private final double minMutationCount = 1;
    private final double maxMutationCount = 5;
    private final double increaseFactor = 1.2;
    private final double decreaseFactor = 0.8;

    public RHEAPlayer(RHEAParams params) {
        super(params, "RHEAPlayer");
    }
    /**返回 RHEAParams 类型的对象，表示该玩家的参数。会被频繁调用**/
    @Override
    public RHEAParams getParameters() {
        return (RHEAParams) parameters;
    }

    /**初始化玩家**/
    @Override
    public void initializePlayer(AbstractGameState state) {
        System.out.println("Test执行：initializePlayer！初始化玩家!");

        this.prevBestValue = Double.NEGATIVE_INFINITY;

        MASTStatistics = new ArrayList<>();//创建 MASTStatistics（多臂老虎机算法的统计数据）用于记录动作的访问次数和累计价值
        for (int i = 0; i < state.getNPlayers(); i++)
            MASTStatistics.add(new HashMap<>());
        population = new ArrayList<>();//初始化种群 population，表示演化算法的初始个体集。
    }
    /**这是关键的决策函数，在游戏状态下通过演化算法选择下一步的动作**/
    @Override
    public AbstractAction _getAction(AbstractGameState stateObs, List<AbstractAction> possibleActions) {
        System.out.println("Test执行：_getAction！选择下一步的动作!");
        ElapsedCpuTimer timer = new ElapsedCpuTimer();  // New timer for this game tick使用定时器 ElapsedCpuTimer 来确保算法不会超时
        timer.setMaxTimeMillis(parameters.budget);
        numIters = 0;
        fmCalls = 0;
        copyCalls = 0;
        repairCount = 0;
        nonRepairCount = 0;
        RHEAParams params = getParameters();
        /**首先根据参数进行种群的初始化或左移操作 然后运行演化过程来改进种群，并最终选择表现最好的个体的第一个动作作为返回值。**/
        if (params.useMAST) {
            if (MASTStatistics == null) {
                MASTStatistics = new ArrayList<>();
                for (int i = 0; i < stateObs.getNPlayers(); i++)
                    MASTStatistics.add(new HashMap<>());
            } else {
                MASTStatistics = MASTStatistics.stream()
                        .map(m -> Utils.decay(m, params.discountFactor))
                        .collect(Collectors.toList());
            }
            mastPlayer = new MASTPlayer(null, 1.0, 0.0, System.currentTimeMillis(), 0.0);
            mastPlayer.setStats(MASTStatistics);
        }
        // Initialise individuals
        if (params.shiftLeft && !population.isEmpty()) {
            population.forEach(i -> i.value = Double.NEGATIVE_INFINITY);  // so that any we don't have time to shift are ignored when picking an action
            for (RHEAIndividual genome : population) {
                if (!budgetLeft(timer)) break;
                System.arraycopy(genome.actions, 1, genome.actions, 0, genome.actions.length - 1);
                // we shift all actions along, and then rollout with repair
                genome.gameStates[0] = stateObs.copy();
                Pair<Integer, Integer> calls = genome.rollout(getForwardModel(), 0, getPlayerID(), true);
                fmCalls += calls.a;
                copyCalls += calls.b;
            }
        } else {
            population = new ArrayList<>();
            for (int i = 0; i < params.populationSize; ++i) {
                if (!budgetLeft(timer)) break;
                population.add(new RHEAIndividual(params.horizon, params.discountFactor, getForwardModel(), stateObs,
                        getPlayerID(), rnd, params.heuristic, params.useMAST ? mastPlayer : randomPlayer));
                fmCalls += population.get(i).length;
                copyCalls += population.get(i).length;
            }
        }

        population.sort(Comparator.naturalOrder());
        initTime = timer.elapsedMillis();
        // Run evolution
        /**演化过程：每轮迭代会调用 runIteration 函数来优化种群**/
        while (budgetLeft(timer)) {
            runIteration();
        }

        timeTaken = timer.elapsedMillis();
        timePerIteration = numIters == 0 ? 0.0 : (timeTaken - initTime) / numIters;
        // Return first action of best individual
        AbstractAction retValue = population.get(0).actions[0];
        List<AbstractAction> actions = getForwardModel().computeAvailableActions(stateObs, params.actionSpace);
        if (!actions.contains(retValue))
            throw new AssertionError("Action chosen is not legitimate " + numIters + ", " + params.shiftLeft);
        return retValue;
    }
    /**检查当前是否还剩下预算来继续执行演化（预算可以是时间、调用次数、迭代次数等）。
     * 根据不同的预算类型（时间、前向模型调用次数等）来判断是否继续迭代。
     * **/
    private boolean budgetLeft(ElapsedCpuTimer timer) {
        System.out.println("Test执行：检查剩余预算！");
        RHEAParams params = getParameters();
        if (params.budgetType == PlayerConstants.BUDGET_TIME) {
            long remaining = timer.remainingTimeMillis();
            return remaining > params.breakMS;
        } else if (params.budgetType == PlayerConstants.BUDGET_FM_CALLS) {
            return fmCalls < params.budget;
        } else if (params.budgetType == PlayerConstants.BUDGET_COPY_CALLS) {
            return copyCalls < params.budget && numIters < params.budget;
        } else if (params.budgetType == PlayerConstants.BUDGET_FMANDCOPY_CALLS) {
            return (fmCalls + copyCalls) < params.budget;
        } else if (params.budgetType == PlayerConstants.BUDGET_ITERATIONS) {
            return numIters < params.budget;
        }
        throw new AssertionError("This should be unreachable : " + params.budgetType);
    }
    /**创建并返回 RHEAPlayer 的一个副本，用于并行计算或多次运行时的玩家状态复制。**/
    @Override
    public RHEAPlayer copy() {
        RHEAParams newParams = (RHEAParams) parameters.copy();
        newParams.setRandomSeed(rnd.nextInt());
        RHEAPlayer retValue = new RHEAPlayer(newParams);
        retValue.setForwardModel(getForwardModel().copy());
        return retValue;
    }
    /**交叉两个个体（父代）的基因，根据不同的交叉类型（如单点、双点、均匀交叉），生成新的子代。**/
    private RHEAIndividual crossover(RHEAIndividual p1, RHEAIndividual p2) {
        switch (getParameters().crossoverType) {
            case NONE: // we just take the first parent
                return new RHEAIndividual(p1);
            case UNIFORM:
                return uniformCrossover(p1, p2);
            case ONE_POINT:
                return onePointCrossover(p1, p2);
            case TWO_POINT:
                return twoPointCrossover(p1, p2);
            default:
                throw new RuntimeException("Unexpected crossover type");
        }
    }
    /**执行均匀交叉，逐位随机选择两个父代中的动作，生成子代。**/
    private RHEAIndividual uniformCrossover(RHEAIndividual p1, RHEAIndividual p2) {
        RHEAIndividual child = new RHEAIndividual(p1);
        copyCalls += child.length;
        int min = Math.min(p1.length, p2.length);
        for (int i = 0; i < min; ++i) {
            if (rnd.nextFloat() >= 0.5f) {
                child.actions[i] = p2.actions[i];
                child.gameStates[i] = p2.gameStates[i]; //.copy();
            }
        }
        return child;
    }
    /**执行单点交叉，从某个点开始，取其中一个父代的基因，生成子代。**/
    private RHEAIndividual onePointCrossover(RHEAIndividual p1, RHEAIndividual p2) {
        RHEAIndividual child = new RHEAIndividual(p1);
        copyCalls += child.length;
        int tailLength = Math.min(p1.length, p2.length) / 2;

        for (int i = 0; i < tailLength; ++i) {
            child.actions[child.length - 1 - i] = p2.actions[p2.length - 1 - i];
            child.gameStates[child.length - 1 - i] = p2.gameStates[p2.length - 1 - i]; //.copy();
        }
        return child;
    }
    /**执行单点交叉，从某个点开始，取其中一个父代的基因，生成子代。**/
    private RHEAIndividual twoPointCrossover(RHEAIndividual p1, RHEAIndividual p2) {
        RHEAIndividual child = new RHEAIndividual(p1);
        copyCalls += child.length;
        int tailLength = Math.min(p1.length, p2.length) / 3;
        for (int i = 0; i < tailLength; ++i) {
            child.actions[i] = p2.actions[i];
            child.gameStates[i] = p2.gameStates[i]; //.copy();
            child.actions[child.length - 1 - i] = p2.actions[p2.length - 1 - i];
            child.gameStates[child.length - 1 - i] = p2.gameStates[p2.length - 1 - i]; //.copy();
        }
        return child;
    }
    /**选择两个个体作为父代，使用不同的选择机制（如锦标赛选择、排名选择）。**/
    RHEAIndividual[] selectParents() {
        RHEAIndividual[] parents = new RHEAIndividual[2];

        switch (getParameters().selectionType) {
            case TOURNAMENT:
                parents[0] = tournamentSelection();
                parents[1] = tournamentSelection();
                break;
            case RANK:
                parents[0] = rankSelection();
                parents[1] = rankSelection();
                break;
            default:
                throw new RuntimeException("Unexpected selection type");
        }

        return parents;
    }
    /**锦标赛选择，从种群中随机挑选若干个体，然后选出表现最好的个体作为父代。**/
    RHEAIndividual tournamentSelection() {
        RHEAIndividual best = null;
        for (int i = 0; i < getParameters().tournamentSize; ++i) {
            int rand = rnd.nextInt(population.size());

            RHEAIndividual current = population.get(rand);
            if (best == null || current.value > best.value)
                best = current;
        }
        return best;
    }
    /**排名选择，将个体按表现排序，基于排名进行选择，排名高的个体有更高的概率被选为父代。**/
    RHEAIndividual rankSelection() {
        population.sort(Comparator.naturalOrder());
        int rankSum = 0;
        for (int i = 0; i < population.size(); ++i)
            rankSum += i + 1;
        int ran = rnd.nextInt(rankSum);
        int p = 0;
        for (int i = 0; i < population.size(); ++i) {
            p += population.size() - (i);
            if (p >= ran)
                return population.get(i);
        }
        throw new RuntimeException("Random Generator generated an invalid goal, goal: " + ran + " p: " + p);
    }


    private void adjustMutationRate(RHEAParams params) {
        prevBestValue = currentBestValue;
        currentBestValue = population.get(0).value;
        // 根据当前表现调整突变率
        if (currentBestValue > prevBestValue) {
            // 如果当前最优值提升，则减少突变率（更趋向于利用）
            params.mutationCount = Math.max((int)(params.mutationCount * decreaseFactor), (int) minMutationCount);
            System.out.println("Test减少 突变率");
        } else if(currentBestValue < prevBestValue){
            // 如果当前最优值没有提升，则增加突变率（更趋向于探索）
            params.mutationCount = Math.min((int)(params.mutationCount * increaseFactor), (int) maxMutationCount);
            System.out.println("Test增加 突变率");
        }
        // 更新前一个最佳值
        System.out.println("调整突变率: 当前最优值 = " + currentBestValue + ", 之前最优值 = " + prevBestValue
                + ", 新的突变次数 = " + params.mutationCount);
    }

    /**
     * Run evolutionary process for one generation
     * 进行一代演化，包括精英保留、交叉操作、变异操作、修复操作（如果需要），并通过 MAST 更新统计数据。最后更新种群和预算。
     */
    private void runIteration() {
        System.out.println("Test执行：runIteration！优化种群!");
        //copy elites
        RHEAParams params = getParameters();
        adjustMutationRate(params);
        List<RHEAIndividual> newPopulation = new ArrayList<>();
        for (int i = 0, max = Math.min(params.eliteCount, population.size()); i < max; ++i) {
            newPopulation.add(new RHEAIndividual(population.get(i)));
        }
        //crossover
        for (int i = 0; i < params.childCount; ++i) {
            RHEAIndividual[] parents = selectParents();
            RHEAIndividual child = crossover(parents[0], parents[1]);
            population.add(child);
        }
        // 突变与备份操作
        for (RHEAIndividual individual : population) {
            Pair<Integer, Integer> calls = individual.mutate(getForwardModel(), getPlayerID(), params.mutationCount);
            fmCalls += calls.a;
            copyCalls += calls.b;
            repairCount += individual.repairCount;
            nonRepairCount += individual.nonRepairCount;
            if (params.useMAST)
                MASTBackup(individual.actions, individual.value, getPlayerID());
        }

        //sort
        population.sort(Comparator.naturalOrder());

        //best ones get moved to the new population
        for (int i = 0; i < Math.min(population.size(), params.populationSize - params.eliteCount); ++i) {
            newPopulation.add(population.get(i));
        }

        population = newPopulation;

        population.sort(Comparator.naturalOrder());
        // Update budgets
        numIters++;
    }

    /**更新 MASTStatistics，即记录每个动作的访问次数和累计价值，用于多臂老虎机算法的改进。**/
    protected void MASTBackup(AbstractAction[] rolloutActions, double delta, int player) {
        System.out.println("Test执行：MASTBackup！记录每个动作的访问次数和累计价值！");
        for (int i = 0; i < rolloutActions.length; i++) {
            AbstractAction action = rolloutActions[i];
            if (action == null)
                break;
            Pair<Integer, Double> stats = MASTStatistics.get(player).getOrDefault(action, new Pair<>(0, 0.0));
            stats.a++;  // visits
            stats.b += delta;   // value
            MASTStatistics.get(player).put(action.copy(), stats);
        }
    }

    /**设置该玩家的预算（如时间、调用次数等），并将其保存到参数中。**/
    @Override
    public void setBudget(int budget) {
        System.out.println("Test执行：setBudget！设置该玩家的预算！");
        parameters.budget = budget;
        parameters.setParameterValue("budget", budget);
    }
    /**返回玩家当前的预算值。**/
    @Override
    public int getBudget() {
        System.out.println("Test执行：getBudget！返回玩家当前的预算值！");
        return parameters.budget;
    }
}
