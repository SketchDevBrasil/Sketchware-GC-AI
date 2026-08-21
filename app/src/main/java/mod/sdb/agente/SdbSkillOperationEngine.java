package mod.sdb.agente;

/** Applies isolated Skill lifecycle operations requested by GC-AI. */
final class SdbSkillOperationEngine {
    private SdbSkillOperationEngine() {
    }

    static boolean canHandle(String operation) {
        return "create_skill".equals(operation) || "update_skill".equals(operation);
    }

    static SdbProjectMutationEngine.Result apply(String scId, SdbEditEngine.Operation operation) {
        boolean update = "update_skill".equals(operation.op);
        SdbSkillManager.ImportResult result = SdbSkillManager.createOrUpdate(
                scId, operation.data, update);
        if (!result.success()) {
            return new SdbProjectMutationEngine.Result(false, false, result.error);
        }
        return new SdbProjectMutationEngine.Result(true, true,
                (update ? "Skill atualizada: " : "Skill candidata criada: ")
                        + result.skill.name + " v" + result.skill.version);
    }
}
