/**
 * generated by Xtext 2.11.0
 */
package com.b2international.snowowl.snomed.ql;

import org.eclipse.emf.ecore.EFactory;

/**
 * <!-- begin-user-doc -->
 * The <b>Factory</b> for the model.
 * It provides a create method for each non-abstract class of the model.
 * <!-- end-user-doc -->
 * @see com.b2international.snowowl.snomed.ql.QlPackage
 * @generated
 */
public interface QlFactory extends EFactory
{
  /**
   * The singleton instance of the factory.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  QlFactory eINSTANCE = com.b2international.snowowl.snomed.ql.impl.QlFactoryImpl.init();

  /**
   * Returns a new object of class '<em>Query</em>'.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return a new object of class '<em>Query</em>'.
   * @generated
   */
  Query createQuery();

  /**
   * Returns a new object of class '<em>Disjunction</em>'.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return a new object of class '<em>Disjunction</em>'.
   * @generated
   */
  Disjunction createDisjunction();

  /**
   * Returns a new object of class '<em>Conjunction</em>'.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return a new object of class '<em>Conjunction</em>'.
   * @generated
   */
  Conjunction createConjunction();

  /**
   * Returns a new object of class '<em>Exclusion</em>'.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return a new object of class '<em>Exclusion</em>'.
   * @generated
   */
  Exclusion createExclusion();

  /**
   * Returns a new object of class '<em>Filter</em>'.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return a new object of class '<em>Filter</em>'.
   * @generated
   */
  Filter createFilter();

  /**
   * Returns a new object of class '<em>Ecl Filter</em>'.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return a new object of class '<em>Ecl Filter</em>'.
   * @generated
   */
  EclFilter createEclFilter();

  /**
   * Returns a new object of class '<em>Term Filter</em>'.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return a new object of class '<em>Term Filter</em>'.
   * @generated
   */
  TermFilter createTermFilter();

  /**
   * Returns the package supported by this factory.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return the package supported by this factory.
   * @generated
   */
  QlPackage getQlPackage();

} //QlFactory
