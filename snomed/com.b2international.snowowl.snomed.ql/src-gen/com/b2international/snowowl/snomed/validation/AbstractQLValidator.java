/*
 * generated by Xtext 2.11.0
 */
package com.b2international.snowowl.snomed.validation;

import com.b2international.snowowl.snomed.ecl.validation.EclValidator;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.EPackage;

public abstract class AbstractQLValidator extends EclValidator {
	
	@Override
	protected List<EPackage> getEPackages() {
		List<EPackage> result = new ArrayList<EPackage>(super.getEPackages());
		result.add(com.b2international.snowowl.snomed.ql.QlPackage.eINSTANCE);
		return result;
	}
	
}
