/*
 * Copyright 2010 Henry Coles
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License. 
 */
package org.pitest.mutationtest.engine.gregor;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.pitest.functional.FunctionalList;
import org.pitest.functional.predicate.True;
import org.pitest.mutationtest.MutationDetails;
import org.pitest.mutationtest.Mutator;
import org.pitest.mutationtest.engine.gregor.mutators.IncrementsMutator;
import org.pitest.mutationtest.engine.gregor.mutators.InvertNegsMutator;
import org.pitest.mutationtest.engine.gregor.mutators.MathMutator;
import org.pitest.mutationtest.engine.gregor.mutators.ReturnValsMutator;
import org.pitest.util.ResourceFolderByteArraySource;

public class TestGregorMutater extends MutatorTestBase {

  public static class HasMultipleMutations {
    public int mutable() {
      int j = 10;
      for (int i = 0; i != 10; i++) {
        j = j << 1;
      }

      return -j;
    }

  }

  @Test
  public void shouldFindMutationsFromAllSuppliedMutators() throws Exception {

    createTesteeWith(MathMutator.MATH_MUTATOR,
        ReturnValsMutator.RETURN_VALS_MUTATOR,
        InvertNegsMutator.INVERT_NEGS_MUTATOR,
        IncrementsMutator.INCREMENTS_MUTATOR);

    final FunctionalList<MutationDetails> actualDetails = findMutationsFor(HasMultipleMutations.class);

    assertTrue(actualDetails
        .contains(descriptionContaining("Replaced Shift Left with Shift Right")));
    assertTrue(actualDetails
        .contains(descriptionContaining("replaced return of integer")));
    assertTrue(actualDetails
        .contains(descriptionContaining("Changed increment")));
    assertTrue(actualDetails
        .contains(descriptionContaining("removed negation")));

  }

  @Test
  public void shouldFindNoMutationsWhenNoMutationOperatorsSupplied()
      throws Exception {
    class VeryMutable {
      @SuppressWarnings("unused")
      public int f(final int i) {
        switch (i) {
        case 0:
          return 1;
        }
        return 0;
      }
    }
    createTesteeWith();
    final FunctionalList<MutationDetails> actualDetails = findMutationsFor(VeryMutable.class);
    assertTrue(actualDetails.isEmpty());

  }

  static enum AnEnum {
    Foo, Bar;
  }

  @Test
  public void shouldNotMutateCodeGeneratedByCompilerToImplementEnums() {
    createTesteeWith(Mutator.ALL.asCollection());
    final Collection<MutationDetails> actualDetails = findMutationsFor(AnEnum.class);
    assertTrue(actualDetails.isEmpty());
  }

  static enum EnumWithCustomConstructor {
    Foo, Bar;

    int i;

    EnumWithCustomConstructor() {
      this.i++;
    }

  }

  @Test
  public void shouldMutateCustomConstructorsAddedToEnums() {
    createTesteeWith(Mutator.ALL.asCollection());
    final Collection<MutationDetails> actualDetails = findMutationsFor(EnumWithCustomConstructor.class);
    assertThat(actualDetails, is(aNonEmptyCollection()));
  }

  private static Matcher<Collection<?>> aNonEmptyCollection() {
    return new TypeSafeMatcher<Collection<?>>() {

      public void describeTo(Description description) {
        description.appendText("a non empty collection");
      }

      @Override
      public boolean matchesSafely(Collection<?> item) {
        return !item.isEmpty();
      }
    };
  }

  public static class HasFinallyAroundReturnStatement {
    public int foo(int t) {
      try {
        return t;
      } finally {
        bar();
      }

    }

    public void bar() {

    }
  }

  @Test
  public void willGenerateMutatationsForBothBranchesOfTryFinallyBlock() {
    createTesteeWith(Mutator.VOID_METHOD_CALLS.asCollection());
    final Collection<MutationDetails> actualDetails = findMutationsFor(HasFinallyAroundReturnStatement.class);
    assertEquals(2, actualDetails.size());
  }
  
  
  public static class HasAssertStatement { 
    public void foo(int i) {
      assert (i+ 20 > 10);
    }
  }
  
  @Test
  public void shouldNotMutateAssertStatments() {
    createTesteeWith(Mutator.NEGATE_CONDITIONALS.asCollection());
    final Collection<MutationDetails> actualDetails = findMutationsFor(HasAssertStatement.class);
    assertEquals(0, actualDetails.size());
  }
  
  public static class HasAssertStatementAndOtherStatements { 
    public int state;
    public void foo(int i) {
      assert (i+ 20 > 10);
      if ( i > 1 ) {
        state = 1;
      }
    }
  }
  
  @Test
  public void shouldMutateOtherStatementsWhenAssertIsPresent() {
    createTesteeWith(Mutator.NEGATE_CONDITIONALS.asCollection());
    final Collection<MutationDetails> actualDetails = findMutationsFor(HasAssertStatementAndOtherStatements.class);
    assertEquals(1, actualDetails.size());
  }
  
  @Test
  public void shouldNotMutateGroovyClasses() {
    createTesteeWith(new ResourceFolderByteArraySource(), True.<MethodInfo> all(),Mutator.ALL.asCollection());
    final Collection<MutationDetails> actualDetails = findMutationsFor("groovy/SomeGroovyCode");
    assertTrue(actualDetails.isEmpty());
  }
  
  @Test
  public void shouldNotMutateGroovyClosures() {
    createTesteeWith(new ResourceFolderByteArraySource(), True.<MethodInfo> all(),Mutator.ALL.asCollection());
    final Collection<MutationDetails> actualDetails = findMutationsFor("groovy/SomeGroovyCode$_mapToString_closure2");
    assertTrue(actualDetails.isEmpty());
  }
}
