package com.tnb.dbp.session;

import com.tnb.dbp.customer.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<UserSession, String> {

    /** Ends every session of this customer, e.g. after a password reset (TNB-5 AC6). */
    @Modifying
    @Query("delete from UserSession s where s.customer = :customer")
    int deleteAllForCustomer(@Param("customer") Customer customer);
}
