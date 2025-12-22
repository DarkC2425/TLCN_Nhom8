module.exports = {
    jwtSecret: process.env.JWT_SECRET || process.env.ACCESS_TOKEN_SECRET,
    expiresIn: '7d'
};
