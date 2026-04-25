package com.shadowfit.repository;

import com.shadowfit.model.exercise.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExercisesRepository extends JpaRepository<Exercise,Long> {
}
