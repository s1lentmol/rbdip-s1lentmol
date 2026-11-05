package com.rbdip.bookstore.customer;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface CustomerRepository extends Repository<Customer, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO customers (first_name, last_name, address, phone)
            VALUES (:firstName, :lastName, :address, :phone)
            ON CONFLICT (first_name, last_name, address, phone) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(String firstName, String lastName, String address, String phone);

    Customer findByFirstNameAndLastNameAndAddressAndPhone(
            String firstName, String lastName, String address, String phone);

    default Customer findOrCreate(String fullName, String address, String phone) {
        int separator = fullName.indexOf(' ');
        if (separator == fullName.length() - 1) {
            separator = -1;
        }
        String firstName = separator < 0 ? fullName : fullName.substring(0, separator);
        String lastName = separator < 0 ? "" : fullName.substring(separator + 1);
        insertIfAbsent(firstName, lastName, address, phone);
        return findByFirstNameAndLastNameAndAddressAndPhone(firstName, lastName, address, phone);
    }
}
