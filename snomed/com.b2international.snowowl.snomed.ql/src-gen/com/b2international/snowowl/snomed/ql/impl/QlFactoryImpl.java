/**
 * generated by Xtext 2.11.0
 */
package com.b2international.snowowl.snomed.ql.impl;

import com.b2international.snowowl.snomed.ql.*;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;

import org.eclipse.emf.ecore.impl.EFactoryImpl;

import org.eclipse.emf.ecore.plugin.EcorePlugin;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model <b>Factory</b>.
 * <!-- end-user-doc -->
 * @generated
 */
public class QlFactoryImpl extends EFactoryImpl implements QlFactory
{
  /**
   * Creates the default factory implementation.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public static QlFactory init()
  {
    try
    {
      QlFactory theQlFactory = (QlFactory)EPackage.Registry.INSTANCE.getEFactory(QlPackage.eNS_URI);
      if (theQlFactory != null)
      {
        return theQlFactory;
      }
    }
    catch (Exception exception)
    {
      EcorePlugin.INSTANCE.log(exception);
    }
    return new QlFactoryImpl();
  }

  /**
   * Creates an instance of the factory.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public QlFactoryImpl()
  {
    super();
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  @Override
  public EObject create(EClass eClass)
  {
    switch (eClass.getClassifierID())
    {
      case QlPackage.QUERY: return createQuery();
      case QlPackage.DISJUNCTION: return createDisjunction();
      case QlPackage.CONJUNCTION: return createConjunction();
      case QlPackage.EXCLUSION: return createExclusion();
      case QlPackage.FILTER: return createFilter();
      case QlPackage.ECL_FILTER: return createEclFilter();
      case QlPackage.TERM_FILTER: return createTermFilter();
      default:
        throw new IllegalArgumentException("The class '" + eClass.getName() + "' is not a valid classifier");
    }
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public Query createQuery()
  {
    QueryImpl query = new QueryImpl();
    return query;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public Disjunction createDisjunction()
  {
    DisjunctionImpl disjunction = new DisjunctionImpl();
    return disjunction;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public Conjunction createConjunction()
  {
    ConjunctionImpl conjunction = new ConjunctionImpl();
    return conjunction;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public Exclusion createExclusion()
  {
    ExclusionImpl exclusion = new ExclusionImpl();
    return exclusion;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public Filter createFilter()
  {
    FilterImpl filter = new FilterImpl();
    return filter;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EclFilter createEclFilter()
  {
    EclFilterImpl eclFilter = new EclFilterImpl();
    return eclFilter;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public TermFilter createTermFilter()
  {
    TermFilterImpl termFilter = new TermFilterImpl();
    return termFilter;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public QlPackage getQlPackage()
  {
    return (QlPackage)getEPackage();
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @deprecated
   * @generated
   */
  @Deprecated
  public static QlPackage getPackage()
  {
    return QlPackage.eINSTANCE;
  }

} //QlFactoryImpl
