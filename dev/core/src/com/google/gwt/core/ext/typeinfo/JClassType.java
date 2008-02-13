/*
 * Copyright 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.core.ext.typeinfo;

import com.google.gwt.core.ext.UnableToCompleteException;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Type used to represent any non-primitive type.
 */
public abstract class JClassType extends JType implements HasAnnotations,
    HasMetaData {

  /**
   * Returns all of the superclasses and superinterfaces for a given type
   * including the type itself.
   */
  protected static Set<JClassType> getFlattenedSuperTypeHierarchy(
      JClassType type) {
    Set<JClassType> typesSeen = new HashSet<JClassType>();
    getFlattenedSuperTypeHierarchyRecursive(type, typesSeen);
    return typesSeen;
  }

  /**
   * Returns <code>true</code> if the rhs array type can be assigned to the
   * lhs array type.
   */
  private static boolean areArraysAssignable(JArrayType lhsType,
      JArrayType rhsType) {
    // areClassTypesAssignable should prevent us from getting here if the types
    // are referentially equal.
    assert (lhsType != rhsType);

    JType lhsComponentType = lhsType.getComponentType();
    JType rhsComponentType = rhsType.getComponentType();

    if (lhsComponentType.isPrimitive() != null
        || rhsComponentType.isPrimitive() != null) {
      /*
       * Arrays are referentially stable so there will only be one int[] no
       * matter how many times it is referenced in the code. So, if either
       * component type is a primitive then we know that we are not assignable.
       */
      return false;
    }

    assert (lhsComponentType instanceof JClassType);
    assert (rhsComponentType instanceof JClassType);

    JClassType thisComponentClass = (JClassType) lhsComponentType;
    JClassType subtypeComponentClass = (JClassType) rhsComponentType;

    return areClassTypesAssignable(thisComponentClass, subtypeComponentClass);
  }

  /**
   * Returns <code>true</code> if the rhsType can be assigned to the lhsType.
   */
  private static boolean areClassTypesAssignable(JClassType lhsType,
      JClassType rhsType) {
    // The supertypes of rhs will include rhs.
    Set<JClassType> rhsSupertypes = getFlattenedSuperTypeHierarchy(rhsType);
    for (JClassType rhsSupertype : rhsSupertypes) {
      if (areClassTypesAssignableNoSupers(lhsType, rhsSupertype)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns <code>true</code> if the lhs and rhs are assignable without
   * consideration of the supertypes of the rhs.
   * 
   * @param lhsType
   * @param rhsType
   * @return
   */
  private static boolean areClassTypesAssignableNoSupers(JClassType lhsType,
      JClassType rhsType) {
    if (lhsType == rhsType) {
      // Done, these are the same types.
      return true;
    }

    if (lhsType == lhsType.getOracle().getJavaLangObject()) {
      // Done, any type can be assigned to object.
      return true;
    }

    /*
     * Get the generic base type, if there is one, for the lhs type and convert
     * it to a raw type if it is generic.
     */
    if (lhsType.isGenericType() != null) {
      lhsType = lhsType.isGenericType().getRawType();
    }

    if (rhsType.isGenericType() != null) {
      // Treat the generic rhs type as a raw type.
      rhsType = rhsType.isGenericType().getRawType();
    }

    // Check for JTypeParameters.
    JTypeParameter lhsTypeParam = lhsType.isTypeParameter();
    JTypeParameter rhsTypeParam = rhsType.isTypeParameter();
    if (lhsTypeParam != null) {
      JBound bounds = lhsTypeParam.getBounds();
      JClassType[] lhsTypeBounds = bounds.getBounds();
      for (JClassType lhsTypeBound : lhsTypeBounds) {
        if (!areClassTypesAssignable(lhsTypeBound, rhsType)) {
          // Done, the rhsType was not assignable to one of the bounds.
          return false;
        }
      }

      // Done, the rhsType was assignable to all of the bounds.
      return true;
    } else if (rhsTypeParam != null) {
      JClassType[] possibleSubtypeBounds = rhsTypeParam.getBounds().getBounds();
      for (JClassType possibleSubtypeBound : possibleSubtypeBounds) {
        if (areClassTypesAssignable(lhsType, possibleSubtypeBound)) {
          // Done, at least one bound is assignable to this type.
          return true;
        }
      }

      return false;
    }

    /*
     * Check for JWildcards. We have not examined this part in great detail
     * since there should not be top level wildcard types.
     */
    JWildcardType lhsWildcard = lhsType.isWildcard();
    JWildcardType rhsWildcard = rhsType.isWildcard();
    if (lhsWildcard != null && rhsWildcard != null) {
      // Both types are wildcards.
      return areWildcardsAssignable(lhsWildcard, rhsWildcard);
    } else if (lhsWildcard != null) {
      // The lhs type is a wildcard but the rhs is not.
      // ? extends T, U OR ? super T, U
      JBound lhsBound = lhsWildcard.getBounds();
      if (lhsBound.isUpperBound() != null) {
        return areClassTypesAssignable(lhsBound.getFirstBound(), rhsType);
      } else {
        // ? super T will reach object no matter what the rhs type is
        return true;
      }
    }

    // Check for JArrayTypes.
    JArrayType lhsArray = lhsType.isArray();
    JArrayType rhsArray = rhsType.isArray();
    if (lhsArray != null) {
      if (rhsArray == null) {
        return false;
      } else {
        return areArraysAssignable(lhsArray, rhsArray);
      }
    } else if (rhsArray != null) {
      // Safe although perhaps not necessary
      return false;
    }

    // Check for JParameterizedTypes and JRawTypes.
    JMaybeParameterizedType lhsMaybeParameterized = lhsType.isMaybeParameterizedType();
    JMaybeParameterizedType rhsMaybeParameterized = rhsType.isMaybeParameterizedType();
    if (lhsMaybeParameterized != null && rhsMaybeParameterized != null) {
      if (lhsMaybeParameterized.getBaseType() == rhsMaybeParameterized.getBaseType()) {
        if (lhsMaybeParameterized.isRawType() != null
            || rhsMaybeParameterized.isRawType() != null) {
          /*
           * Any raw type can be assigned to or from any parameterization of its
           * generic type.
           */
          return true;
        }

        assert (lhsMaybeParameterized.isRawType() == null && rhsMaybeParameterized.isRawType() == null);
        JParameterizedType lhsParameterized = lhsMaybeParameterized.isParameterized();
        JParameterizedType rhsParameterized = rhsMaybeParameterized.isParameterized();
        assert (lhsParameterized != null && rhsParameterized != null);

        return areTypeArgumentsAssignable(lhsParameterized, rhsParameterized);
      }
    }

    // Default to not being assignable.
    return false;
  }

  /**
   * Returns <code>true</code> if the type arguments of the rhs parameterized
   * type are assignable to the type arguments of the lhs parameterized type.
   */
  private static boolean areTypeArgumentsAssignable(JParameterizedType lhsType,
      JParameterizedType rhsType) {
    // areClassTypesAssignable should prevent us from getting here if the types
    // are referentially equal.
    assert (lhsType != rhsType);
    assert (lhsType.getBaseType() == rhsType.getBaseType());

    JClassType[] lhsTypeArgs = lhsType.getTypeArgs();
    JClassType[] rhsTypeArgs = rhsType.getTypeArgs();
    JGenericType lhsBaseType = lhsType.getBaseType();

    // Compare at least as many formal type parameters as are declared on the
    // generic base type. gwt.typeArgs could cause more types to be included.

    JTypeParameter[] lhsTypeParams = lhsBaseType.getTypeParameters();
    for (int i = 0; i < lhsTypeParams.length; ++i) {
      if (!doesTypeArgumentContain(lhsTypeArgs[i], rhsTypeArgs[i])) {
        return false;
      }
    }

    return true;
  }

  /**
   * Returns <code>true</code> if the rhsWildcard can be assigned to the
   * lhsWildcard. This method does not consider supertypes of either lhs or rhs.
   */
  private static boolean areWildcardsAssignable(JWildcardType lhsWildcard,
      JWildcardType rhsWildcard) {
    // areClassTypesAssignable should prevent us from getting here if the types
    // are referentially equal.
    assert (lhsWildcard != rhsWildcard);
    assert (lhsWildcard != null && rhsWildcard != null);

    JBound lhsBound = lhsWildcard.getBounds();
    JBound rhsBound = rhsWildcard.getBounds();

    if (lhsBound.isUpperBound() != null && rhsBound.isUpperBound() != null) {
      // lhsType: ? extends T, rhsType: ? extends U
      return areClassTypesAssignable(lhsBound.getFirstBound(),
          rhsBound.getFirstBound());
    } else if (lhsBound.isLowerBound() != null
        && rhsBound.isLowerBound() != null) {
      // lhsType: ? super T, rhsType ? super U
      return areClassTypesAssignable(rhsBound.getFirstBound(),
          lhsBound.getFirstBound());
    }

    return false;
  }

  /**
   * A restricted version of areClassTypesAssignable that is used for comparing
   * the type arguments of parameterized types, where the lhsTypeArg is the
   * container.
   */
  private static boolean doesTypeArgumentContain(JClassType lhsTypeArg,
      JClassType rhsTypeArg) {
    if (lhsTypeArg == rhsTypeArg) {
      return true;
    }

    // Check for wildcard types
    JWildcardType lhsWildcard = lhsTypeArg.isWildcard();
    JWildcardType rhsWildcard = rhsTypeArg.isWildcard();

    if (lhsWildcard != null) {
      if (rhsWildcard != null) {
        return areWildcardsAssignable(lhsWildcard, rhsWildcard);
      } else {
        // LHS is a wildcard but the RHS is not.
        JBound lhsBound = lhsWildcard.getBounds();
        if (lhsBound.isLowerBound() == null) {
          return areClassTypesAssignable(lhsBound.getFirstBound(), rhsTypeArg);
        } else {
          return areClassTypesAssignable(rhsTypeArg, lhsBound.getFirstBound());
        }
      }
    }

    /*
     * At this point the arguments are not the same and they are not wildcards
     * so, they cannot be assignable, Eh.
     */
    return false;
  }

  private static void getFlattenedSuperTypeHierarchyRecursive(JClassType type,
      Set<JClassType> typesSeen) {
    if (typesSeen.contains(type)) {
      return;
    }
    typesSeen.add(type);

    // Superclass
    JClassType superclass = type.getSuperclass();
    if (superclass != null) {
      getFlattenedSuperTypeHierarchyRecursive(superclass, typesSeen);
    }

    // Check the interfaces
    JClassType[] intfs = type.getImplementedInterfaces();
    for (JClassType intf : intfs) {
      getFlattenedSuperTypeHierarchyRecursive(intf, typesSeen);
    }
  }

  public abstract void addImplementedInterface(JClassType intf);

  public abstract void addMetaData(String tagName, String[] values);

  public abstract void addModifierBits(int bits);

  public abstract JConstructor findConstructor(JType[] paramTypes);

  public abstract JField findField(String name);

  public abstract JMethod findMethod(String name, JType[] paramTypes);

  public abstract JClassType findNestedType(String typeName);

  public abstract <T extends Annotation> T getAnnotation(
      Class<T> annotationClass);

  public abstract int getBodyEnd();

  public abstract int getBodyStart();

  public abstract CompilationUnitProvider getCompilationUnit();

  public abstract JConstructor getConstructor(JType[] paramTypes)
      throws NotFoundException;

  public abstract JConstructor[] getConstructors();

  public abstract JClassType getEnclosingType();

  public abstract JClassType getErasedType();

  public abstract JField getField(String name);

  public abstract JField[] getFields();

  public abstract JClassType[] getImplementedInterfaces();

  public abstract String[][] getMetaData(String tagName);

  public abstract String[] getMetaDataTags();

  public abstract JMethod getMethod(String name, JType[] paramTypes)
      throws NotFoundException;

  /*
   * Returns the declared methods of this class (not any superclasses or
   * superinterfaces).
   */
  public abstract JMethod[] getMethods();

  public abstract String getName();

  public abstract JClassType getNestedType(String typeName)
      throws NotFoundException;

  public abstract JClassType[] getNestedTypes();

  public abstract TypeOracle getOracle();

  public abstract JMethod[] getOverloads(String name);

  /**
   * Iterates over the most-derived declaration of each unique overridable
   * method available in the type hierarchy of the specified type, including
   * those found in superclasses and superinterfaces. A method is overridable if
   * it is not <code>final</code> and its accessibility is <code>public</code>,
   * <code>protected</code>, or package protected.
   * 
   * Deferred binding generators often need to generate method implementations;
   * this method offers a convenient way to find candidate methods to implement.
   * 
   * Note that the behavior does not match
   * {@link Class#getMethod(String, Class[])}, which does not return the most
   * derived method in some cases.
   * 
   * @return an array of {@link JMethod} objects representing overridable
   *         methods
   */
  public abstract JMethod[] getOverridableMethods();

  public abstract JPackage getPackage();

  public abstract JClassType[] getSubtypes();

  public abstract JClassType getSuperclass();

  public abstract String getTypeHash() throws UnableToCompleteException;

  public abstract boolean isAbstract();

  public abstract boolean isAnnotationPresent(
      Class<? extends Annotation> annotationClass);

  public boolean isAssignableFrom(JClassType possibleSubtype) {
    return areClassTypesAssignable(this, possibleSubtype);
  }

  public boolean isAssignableTo(JClassType possibleSupertype) {
    return areClassTypesAssignable(possibleSupertype, this);
  }

  /**
   * Determines if the class can be constructed using a simple <code>new</code>
   * operation. Specifically, the class must
   * <ul>
   * <li>be a class rather than an interface, </li>
   * <li>have either no constructors or a parameterless constructor, and</li>
   * <li>be a top-level class or a static nested class.</li>
   * </ul>
   * 
   * @return <code>true</code> if the type is default instantiable, or
   *         <code>false</code> otherwise
   */
  public abstract boolean isDefaultInstantiable();

  public abstract JGenericType isGenericType();

  @Override
  public abstract JClassType isInterface();

  /**
   * Tests if this type is a local type (within a method).
   * 
   * @return true if this type is a local type, whether it is named or
   *         anonymous.
   */
  public abstract boolean isLocalType();

  /**
   * Tests if this type is contained within another type.
   * 
   * @return true if this type has an enclosing type, false if this type is a
   *         top-level type
   */
  public abstract boolean isMemberType();

  public abstract boolean isPrivate();

  public abstract boolean isProtected();

  public abstract boolean isPublic();

  public abstract boolean isStatic();

  public abstract void setSuperclass(JClassType type);

  @Override
  public String toString() {
    return this.getQualifiedSourceName();
  }

  protected abstract void acceptSubtype(JClassType me);

  protected abstract int getModifierBits();

  protected abstract void getOverridableMethodsOnSuperclassesAndThisClass(
      Map<String, JMethod> methodsBySignature);

  /**
   * Gets the methods declared in interfaces that this type extends. If this
   * type is a class, its own methods are not added. If this type is an
   * interface, its own methods are added. Used internally by
   * {@link #getOverridableMethods()}.
   * 
   * @param methodsBySignature
   */
  protected abstract void getOverridableMethodsOnSuperinterfacesAndMaybeThisInterface(
      Map<String, JMethod> methodsBySignature);

  protected JMaybeParameterizedType isMaybeParameterizedType() {
    return null;
  }

  protected final String makeCompoundName(JClassType type) {
    if (type.getEnclosingType() == null) {
      return type.getSimpleSourceName();
    } else {
      return makeCompoundName(type.getEnclosingType()) + "."
          + type.getSimpleSourceName();
    }
  }

  /**
   * Tells this type's superclasses and superinterfaces about it.
   */
  protected abstract void notifySuperTypesOf(JClassType me);

  protected abstract void removeSubtype(JClassType me);

  abstract void addConstructor(JConstructor ctor);

  abstract void addField(JField field);

  abstract void addMethod(JMethod method);

  abstract void addNestedType(JClassType type);

  abstract JClassType findNestedTypeImpl(String[] typeName, int index);

  /**
   * Returns all of the annotations declared or inherited by this instance. 
   * 
   * NOTE: This method is for testing purposes only.
   */
  abstract Annotation[] getAnnotations();

  /**
   * Returns all of the annotations declared on this instance. 
   * 
   * NOTE: This method is for testing purposes only.
   */
  abstract Annotation[] getDeclaredAnnotations();

  @Override
  abstract JClassType getSubstitutedType(JParameterizedType parameterizedType);

  abstract void notifySuperTypes();

  /**
   * Removes references to this instance from all of its super types.
   */
  abstract void removeFromSupertypes();
}
