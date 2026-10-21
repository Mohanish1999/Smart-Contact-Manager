package com.smart.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name="USER")
public class User {
	@Id                                                                
	@GeneratedValue(strategy=GenerationType.AUTO)
	public int id;
	@NotBlank(message = "Name is required")
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
	public String name;
	@NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
	@Column(unique=true)
	public String email;
	@NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
	public String password;
	
	public String role;
	public boolean enabled;
	
	public String imageurl;
	 @Size(max = 500, message = "About section cannot exceed 500 characters")
	  @Column(length = 500)
	public String about;
	
	@OneToMany(cascade = CascadeType.ALL , fetch = FetchType.LAZY, mappedBy="user")
	private List<Contact> contacts = new ArrayList<>();
	
	public User() {
		super();
		// TODO Auto-generated constructor stub
	}
	public User(int id, String name, String email, String password, String role, boolean enabled, String imageurl,
			String about) {
		super();
		this.id = id;
		this.name = name;
		this.email = email;
		this.password = password;
		this.role = role;
		this.enabled = enabled;
		this.imageurl = imageurl;
		this.about = about;
	}
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public String getRole() {
		return role;
	}
	public void setRole(String role) {
		this.role = role;
	}
	public boolean isEnabled() {
		return enabled;
	}
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	public String getImageurl() {
		return imageurl;
	}
	public void setImageurl(String imageurl) {
		this.imageurl = imageurl;
	}
	public String getAbout() {
		return about;
	}
	public void setAbout(String about) {
		this.about = about;
	}
	@Override
	public String toString() {
		// Deliberately omits the password hash and the (lazy) contacts collection.
		return "User [id=" + id + ", name=" + name + ", email=" + email + ", role=" + role
				+ ", enabled=" + enabled + "]";
	}
	
	
}
