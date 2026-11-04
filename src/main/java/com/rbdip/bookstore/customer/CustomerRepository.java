package com.rbdip.bookstore.customer;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface CustomerRepository extends Repository<Customer, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO customers (full_name, address, phone)
            VALUES (:fullName, :address, :phone)
            ON CONFLICT (full_name, address, phone) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(String fullName, String address, String phone);

    Customer findByFullNameAndAddressAndPhone(String fullName, String address, String phone);

    default Customer findOrCreate(String fullName, String address, String phone) {
        insertIfAbsent(fullName, address, phone);
        return findByFullNameAndAddressAndPhone(fullName, address, phone);
    }
}
