package com.example.skills

class SkillRegistry {

    private val skills = mutableListOf<FridaySkill>()

    init {
        registerSkill(DeviceControlSkill())
        registerSkill(SystemInfoSkill())
        registerSkill(AppLauncherSkill())
        registerSkill(SearchWebSkill())
        registerSkill(ClockTimerSkill())
        registerSkill(CommunicationSkill())
        registerSkill(MapsNavigationSkill())
        registerSkill(CameraMediaSkill())
        registerSkill(SettingsSystemSkill())
        registerSkill(UIAutomationSkill())
        registerSkill(MultiStepSkill())
    }

    fun registerSkill(skill: FridaySkill) {
        skills.removeAll { it.id == skill.id }
        skills.add(skill)
    }

    fun getSkills(): List<FridaySkill> = skills.toList()

    fun findSkillForIntent(intent: String, rawText: String): FridaySkill? {
        return skills.firstOrNull { it.canHandle(intent, rawText) }
    }
}
