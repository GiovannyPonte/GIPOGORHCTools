package com.gipogo.rhctools.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CanonicalInputViewModelsTest {
    @Test fun fickWithHemoglobinInGdlAndGlProducesSameCanonicalState() {
        fun calculate(value: String, unit: FickViewModel.HbUnit): FickViewModel.State {
            val vm = FickViewModel()
            vm.setWeight("70")
            vm.setHeight("175")
            vm.setSaO2("97")
            vm.setSvO2("65")
            if (unit == FickViewModel.HbUnit.G_L) vm.toggleHbUnit()
            vm.setHb(value)
            vm.calculate()
            return vm.state.value
        }
        val gdl = calculate("14", FickViewModel.HbUnit.G_DL)
        val gl = calculate("140", FickViewModel.HbUnit.G_L)
        assertEquals(14.0, gl.hemoglobinGdl!!, 1e-9)
        assertEquals(gdl.bsa!!, gl.bsa!!, 1e-9)
        assertEquals(gdl.cardiacOutputLMin!!, gl.cardiacOutputLMin!!, 1e-9)
        assertEquals(gdl.cardiacIndexLMinM2!!, gl.cardiacIndexLMinM2!!, 1e-9)
    }

    @Test fun fickMetricAndImperialAnthropometricsProduceSameCanonicalState() {
        fun baseVm() = FickViewModel().apply {
            setSaO2("97"); setSvO2("65"); setHb("14")
        }
        val metric = baseVm().apply { setWeight("70"); setHeight("175"); calculate() }.state.value
        val imperialVm = baseVm().apply {
            setWeight("70"); toggleWeightUnit()
            setHeight("175"); toggleHeightUnit()
            calculate()
        }
        val imperial = imperialVm.state.value
        assertEquals(70.0, imperial.weightKg!!, 0.02)
        assertEquals(175.0, imperial.heightCm!!, 0.2)
        assertEquals(metric.cardiacOutputLMin!!, imperial.cardiacOutputLMin!!, 0.01)
    }

    @Test fun cpoAlternateUnitsExposeIdenticalCanonicalInputsAndOutput() {
        val canonical = CpoViewModel().apply {
            setMAP("80"); setCO("5"); setBSA("2"); calculate()
        }.state.value.result
        val alternate = CpoViewModel().apply {
            setMAP((80.0 / 7.50062).toString()); toggleMapUnit()
            setCO((5.0 / 60.0).toString()); toggleCoUnit()
            setBSA("2"); calculate()
        }.state.value.result
        assertNotNull(canonical); assertNotNull(alternate)
        assertEquals(canonical!!.mapMmHg, alternate!!.mapMmHg, 1e-8)
        assertEquals(canonical.cardiacOutputLMin, alternate.cardiacOutputLMin, 1e-8)
        assertEquals(canonical.cpoWatts, alternate.cpoWatts, 1e-8)
    }
}
