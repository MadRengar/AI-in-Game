package players.rhea;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import evaluation.optimisation.TunableParameters;
import org.json.simple.JSONObject;
import players.PlayerParameters;

import java.util.Arrays;
/**RHEAParams.java
 * 这是算法参数的配置文件，负责定义和管理RHEA的各种参数。
 * **/
public class RHEAParams extends PlayerParameters
{

    public int horizon = 10;//搜索树的深度，即在每一代中算法能够模拟的动作序列长度。
    public double discountFactor = 0.9;//折扣因子，通常用于评估未来动作的价值。
    public int populationSize = 10;//种群的大小，即每代中有多少个解（动作序列）。
    public int eliteCount = 2;//精英个体的数量，这些精英会直接进入下一代而不进行变异。
    public int childCount = 10;//每代产生的后代数量。
    public int mutationCount = 1;//在每一代中进行变异的次数
    public RHEAEnums.SelectionType selectionType = RHEAEnums.SelectionType.TOURNAMENT;//选择父代的方式，当前支持“TOURNAMENT”和“RANK”。
    public int tournamentSize = 4;
    public RHEAEnums.CrossoverType crossoverType = RHEAEnums.CrossoverType.UNIFORM;
    public boolean shiftLeft;//决定是否在种群中执行左移操作，即在某些情况下，将动作序列左移并填充新动作。
    public IStateHeuristic heuristic = AbstractGameState::getGameScore;
    public boolean useMAST;//是否使用MAST（Move-Average Sampling Technique）来辅助搜索。

    /**构造函数，用于定义和初始化 RHEA 算法中的可调参数，并为每个参数设定默认值和可能的取值范围。**/
    public RHEAParams() {
        //每个参数都有一个默认值（如 horizon 的默认值是 10），以及一组可选值（如 Arrays.asList(1, 3, 5, 10, 20, 30)）。
        addTunableParameter("horizon", 10, Arrays.asList(1, 3, 5, 10, 20, 30));
        addTunableParameter("discountFactor", 0.9, Arrays.asList(0.5, 0.8, 0.9, 0.95, 0.99, 0.999, 1.0));
        addTunableParameter("populationSize", 10, Arrays.asList(6, 8, 10, 12, 14, 16, 18, 20));
        addTunableParameter("eliteCount", 2, Arrays.asList(2, 4, 6, 8, 10, 12, 14, 16, 18, 20));
        addTunableParameter("childCount", 10, Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        addTunableParameter("selectionType", RHEAEnums.SelectionType.TOURNAMENT, Arrays.asList(RHEAEnums.SelectionType.values()));
        addTunableParameter("tournamentSize", 4, Arrays.asList(1, 2, 3, 4, 5, 6));
        addTunableParameter("crossoverType", RHEAEnums.CrossoverType.UNIFORM, Arrays.asList(RHEAEnums.CrossoverType.values()));
        addTunableParameter("shiftLeft", false, Arrays.asList(false, true));
        addTunableParameter("mutationCount", 1, Arrays.asList(1, 3, 10));
        addTunableParameter("heuristic", (IStateHeuristic) AbstractGameState::getGameScore);
        addTunableParameter("useMAST", false, Arrays.asList(false, true));
    }

    /**该方法用于重置所有参数为当前的调节值。其目的是确保在每次运行算法前，所有参数都被更新为最新的设置。**/
    @Override
    public void _reset() {
        super._reset();
        horizon = (int) getParameterValue("horizon");
        discountFactor = (double) getParameterValue("discountFactor");
        populationSize = (int) getParameterValue("populationSize");
        eliteCount = (int) getParameterValue("eliteCount");
        childCount = (int) getParameterValue("childCount");
        selectionType = (RHEAEnums.SelectionType) getParameterValue("selectionType");
        tournamentSize = (int) getParameterValue("tournamentSize");
        crossoverType = (RHEAEnums.CrossoverType) getParameterValue("crossoverType");
        shiftLeft = (boolean) getParameterValue("shiftLeft");
        mutationCount = (int) getParameterValue("mutationCount");
        useMAST = (boolean) getParameterValue("useMAST");
        heuristic = (IStateHeuristic) getParameterValue("heuristic");
        if (heuristic instanceof TunableParameters) {
            TunableParameters tunableHeuristic = (TunableParameters) heuristic;
            for (String name : tunableHeuristic.getParameterNames()) {
                tunableHeuristic.setParameterValue(name, this.getParameterValue("heuristic." + name));
            }
        }
    }
/**这个方法用于创建并返回一个 RHEAParams 对象的副本。通常在需要创建新对象但保持原有参数不变的情况下使用。**/
    @Override
    protected RHEAParams _copy() {
        return new RHEAParams();
    }

/**该方法负责使用当前的 RHEAParams 参数实例化一个新的 RHEAPlayer 对象，并返回该对象。**/
    @Override
    public RHEAPlayer instantiate() {
        return new RHEAPlayer(this);//RHEAPlayer 是算法的实际执行类，instantiate() 方法允许在参数设置完成后，创建一个基于这些参数的玩家对象，以便在游戏中使用。
    }

    public IStateHeuristic getHeuristic() {
        return heuristic;//这个方法用于返回当前使用的启发式函数（IStateHeuristic）。
    }

}
