DROP TABLE IF EXISTS auth_users;

CREATE TABLE auth_users (
    id INT PRIMARY KEY,
    username VARCHAR(50),
    password VARCHAR(500),
    salt VARCHAR(100),
    algorithm ENUM('PLAIN', 'MD5', 'SHA1', 'SHA256', 'BCRYPT', 'BCRYPT_LOW_ITERATION'),
    level INT,
    email VARCHAR(100),
    role VARCHAR(20)
);

-- Application user has full access (for functional purposes)
GRANT ALL ON auth_users TO application;
