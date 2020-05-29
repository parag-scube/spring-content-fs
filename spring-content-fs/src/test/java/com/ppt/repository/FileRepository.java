package com.ppt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import com.ppt.entity.File;


@RepositoryRestResource(path = "files", collectionResourceRel = "files")
public interface FileRepository extends JpaRepository<File, Long> {

}
