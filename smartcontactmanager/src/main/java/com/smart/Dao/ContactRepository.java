package com.smart.Dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smart.entity.Contact;
import com.smart.entity.User;

public interface ContactRepository extends JpaRepository<Contact, Integer> {

	@Query("from Contact as c where c.user.id=:userId")
	public Page<Contact> findContactsByUser(@Param("userId")int userId, Pageable pePageable );

	public List<Contact> findByNameContainingAndUser(String name, User user);

	/** Ownership is part of the query: a contact of another user is simply not found. */
	Optional<Contact> findByCidAndUserId(int cid, int userId);

	/** {@code pattern} is a lower-case LIKE pattern in which % _ and ! have been escaped with '!'. */
	@Query("select c from Contact c where c.user.id = :userId and ("
			+ "lower(c.name) like :pattern escape '!' or lower(c.secondname) like :pattern escape '!' "
			+ "or lower(c.email) like :pattern escape '!' or lower(c.work) like :pattern escape '!' "
			+ "or c.phone like :pattern escape '!')")
	Page<Contact> searchContacts(@Param("userId") int userId, @Param("pattern") String pattern, Pageable pageable);

	long countByUserId(int userId);

	@Query("select count(c) from Contact c where c.user.id = :userId and c.email is not null and c.email <> ''")
	long countWithEmail(@Param("userId") int userId);

	@Query("select count(c) from Contact c where c.user.id = :userId and c.image is not null "
			+ "and c.image not in ('', 'default.png', 'contact.png')")
	long countWithPhoto(@Param("userId") int userId);

	List<Contact> findTop5ByUserIdOrderByCidDesc(int userId);

	@Query("select c.image from Contact c where c.user.id = :userId and c.image is not null")
	List<String> findImageNamesByUserId(@Param("userId") int userId);
}
