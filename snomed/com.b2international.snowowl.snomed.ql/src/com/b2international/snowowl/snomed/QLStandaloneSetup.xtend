/*
 * generated by Xtext 2.11.0
 */
package com.b2international.snowowl.snomed


/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
class QLStandaloneSetup extends QLStandaloneSetupGenerated {

	def static void doSetup() {
		new QLStandaloneSetup().createInjectorAndDoEMFRegistration()
	}
}
